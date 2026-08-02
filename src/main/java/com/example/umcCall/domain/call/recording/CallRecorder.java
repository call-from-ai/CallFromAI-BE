package com.example.umcCall.domain.call.recording;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

/**
 * 통화 한 건의 오디오를 <b>타임라인 믹스</b>로 디스크에 스풀한다. 사용자 업스트림 PCM과 AI 다운스트림 wav를
 * 파일 하나에 얹어, 통화가 끝나면 그 파일이 곧 재생 가능한 wav가 된다(다시듣기).
 *
 * <p><b>왜 이어붙이기(concat)가 아니라 믹스인가</b>: 이어붙이면 침묵·생각하는 시간이 통째로 빠져 길이가
 * {@code callTime}과 크게 어긋나고, 사용자 발화 구간을 잘라낼 근거(STT 타임스탬프)가 필요해지며, 끼어들기가
 * 겹치는 순간을 표현할 수 없다. 믹스는 업스트림을 통째로 스풀하므로 절단이 아예 불필요하다.
 *
 * <p><b>왜 랜덤 액세스가 필수인가</b>: AI 대사가 사용자의 침묵 구간보다 길면 <b>이미 써둔 뒤쪽에 겹쳐 써야</b>
 * 한다. 앞에서 뒤로만 흐르는 스트리밍 인코더로는 구조적으로 불가능하다. 그래서 {@link RandomAccessFile}에
 * read-modify-write로 더한다. 아직 안 쓴 구간은 파일 구멍(0 = 무음)이라 침묵이 공짜로 표현된다.
 *
 * <p><b>트랙마다 커서가 있다</b>({@link Track}). 도착 시각에 그대로 얹지 않는 이유는 두 소스 모두
 * <b>실시간보다 빠르게 도착하는 구간</b>이 있어서다 — 프론트는 {@code CALL_READY} 전에 버퍼링한 프레임을
 * 한꺼번에 flush하고, TTS는 문장 wav를 재생 속도보다 빨리 뱉는다(#117). 도착 시각에 얹으면 그 구간이
 * 통째로 겹쳐 뭉개진다. 커서는 "직전 소리가 끝난 지점"과 "지금"의 <b>늦은 쪽</b>을 골라 이걸 막는다.
 *
 * <p><b>fail-open이다.</b> 녹음 실패가 통화·전사에 영향을 주면 안 되므로 쓰기 예외는 여기서 삼키고
 * 그 통화의 녹음만 포기한다({@link #finish()}가 빈 값). 호출부는 예외를 받지 않는다.
 *
 * <p>스레드 안전하다 — 업스트림은 WS 수신 스레드, AI는 통화 워커가 쓰므로 파일·커서 접근을 통째로 직렬화한다.
 * 프레임이 작아(수 KB) 경합은 무시할 수준이다.
 */
@Slf4j
public final class CallRecorder {

    /** 녹음 기준 샘플레이트. 사용자 업스트림(16kHz)을 원본 그대로 두고 AI(24kHz)를 여기 맞춘다. */
    public static final int SAMPLE_RATE = 16_000;

    private static final int BYTES_PER_SAMPLE = 2;
    private static final int FRAMES_PER_MS = SAMPLE_RATE / 1000;

    private final Path spoolPath;
    private final RandomAccessFile spool;
    private final LongSupplier nanoClock;
    private final long startedAtNanos;

    private final Track user = new Track();
    private final Track ai = new Track();

    private boolean failed;
    private boolean finished;

    /** 스풀 파일을 만들고 녹음 원점(t=0)을 잡는다. 파일을 못 만들면 예외 — 호출부가 녹음 없이 진행한다. */
    public static CallRecorder start(Path spoolPath) throws IOException {
        return start(spoolPath, System::nanoTime);
    }

    static CallRecorder start(Path spoolPath, LongSupplier nanoClock) throws IOException {
        Path parent = spoolPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        RandomAccessFile spool = new RandomAccessFile(spoolPath.toFile(), "rw");
        spool.setLength(0);
        // 헤더 자리를 미리 비워둔다 — finish()에서 여기에 써 넣으면 복사 없이 파일이 그대로 wav가 된다.
        spool.write(new byte[WavCodec.HEADER_BYTES]);
        return new CallRecorder(spoolPath, spool, nanoClock);
    }

    private CallRecorder(Path spoolPath, RandomAccessFile spool, LongSupplier nanoClock) {
        this.spoolPath = spoolPath;
        this.spool = spool;
        this.nanoClock = nanoClock;
        this.startedAtNanos = nanoClock.getAsLong();
    }

    /** 사용자 마이크 프레임(16kHz 모노 raw PCM)을 타임라인에 얹는다. */
    public synchronized void writeUpstream(byte[] pcm) {
        if (failed || finished || pcm.length < BYTES_PER_SAMPLE) {
            return;
        }
        try {
            user.append(decode(pcm));
        } catch (IOException | RuntimeException e) {
            markFailed("업스트림 스풀 실패", e);
        }
    }

    /**
     * AI 대사 wav 한 조각(CLOVA Voice 24kHz)을 16kHz로 낮춰 타임라인에 얹는다.
     * <p>한 턴이 문장 단위로 <b>여러 조각</b>이라(#117) 커서가 조각을 이어 붙인다 — 클라이언트가
     * 큐에 넣어 순서대로 재생하는 모습과 같은 배치가 된다.
     */
    public synchronized void writeAiWav(byte[] wav) {
        if (failed || finished || wav.length == 0) {
            return;
        }
        try {
            ai.append(WavCodec.decode(wav).resampleTo(SAMPLE_RATE).samples());
        } catch (IOException | RuntimeException e) {
            markFailed("AI 오디오 스풀 실패", e);
        }
    }

    /**
     * 끼어들기로 클라이언트가 재생 큐를 버렸다 — AI 커서를 "지금"으로 되돌린다.
     * <p>커서를 그대로 두면 <b>버려진 소리가 차지하던 시간만큼</b> 다음 대사가 뒤로 밀려, 녹음에서만
     * AI가 몇 초씩 늦게 대답하는 것처럼 들린다. 이미 써넣은 소리는 <b>지우지 않는다</b> — 서버가 실제로
     * 내보낸 것이고, 사용자가 어디까지 들었는지는 서버가 알 수 없다.
     */
    public synchronized void resetAiCursor() {
        ai.rewind();
    }

    /**
     * 녹음을 마감하고 헤더를 채워 재생 가능한 wav로 만든다.
     *
     * @return 완성된 wav 경로. 녹음이 실패했거나 소리가 한 조각도 안 들어왔으면 <b>빈 값</b>(스풀은 지운다).
     */
    public synchronized Optional<Path> finish() {
        if (finished) {
            return Optional.empty();
        }
        finished = true;
        try {
            long dataBytes = spool.length() - WavCodec.HEADER_BYTES;
            if (failed || dataBytes <= 0) {
                closeQuietly();
                deleteSpool();
                return Optional.empty();
            }
            spool.seek(0);
            spool.write(WavCodec.header((int) dataBytes, SAMPLE_RATE));
            spool.close();
            return Optional.of(spoolPath);
        } catch (IOException e) {
            log.error("[Recording] 녹음 마감 실패 → 폐기. path={}", spoolPath, e);
            closeQuietly();
            deleteSpool();
            return Optional.empty();
        }
    }

    /** 녹음을 버린다(통화가 성립하지 않았거나 업로드가 필요 없을 때). 스풀 파일까지 지운다. */
    public synchronized void abort() {
        finished = true;
        closeQuietly();
        deleteSpool();
    }

    /** 녹음 원점부터 지금까지의 프레임(=샘플) 수. 타임라인 위치의 기준이다. */
    private long nowFrame() {
        long elapsedMs = (nanoClock.getAsLong() - startedAtNanos) / 1_000_000L;
        return elapsedMs * FRAMES_PER_MS;
    }

    /**
     * 소리 한 덩어리를 {@code startFrame} 위치에 <b>더한다</b>(덮어쓰기가 아니다).
     * 이미 소리가 있는 구간이면 두 파형이 섞이고(사용자와 AI가 동시에 말한 구간), 없으면 그대로 놓인다.
     */
    private void mix(long startFrame, short[] samples) throws IOException {
        long bytePos = WavCodec.HEADER_BYTES + startFrame * BYTES_PER_SAMPLE;
        int newBytes = samples.length * BYTES_PER_SAMPLE;

        // 파일 끝을 넘어가는 부분은 아직 아무 소리도 없다 — 겹치는 앞부분만 읽어와 더한다.
        int overlap = (int) Math.max(0, Math.min(newBytes, spool.length() - bytePos));
        byte[] existing = new byte[overlap];
        if (overlap > 0) {
            spool.seek(bytePos);
            spool.readFully(existing);
        }

        ByteBuffer merged = ByteBuffer.allocate(newBytes).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer previous = ByteBuffer.wrap(existing).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < samples.length; i++) {
            int base = (i * BYTES_PER_SAMPLE + 1 < overlap) ? previous.getShort(i * BYTES_PER_SAMPLE) : 0;
            merged.putShort(clamp(base + samples[i]));
        }
        spool.seek(bytePos);
        spool.write(merged.array());
    }

    /** 더한 값이 16-bit를 넘으면 잘라낸다. 넘긴 채 두면 부호가 뒤집혀 '딱딱' 하는 잡음이 된다. */
    private static short clamp(int sample) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample));
    }

    /** 리틀엔디언 raw PCM 바이트를 샘플로 편다. */
    private static short[] decode(byte[] pcm) {
        return WavCodec.toSamples(ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN), 0, pcm.length);
    }

    private void markFailed(String message, Exception cause) {
        failed = true;
        log.error("[Recording] {} → 이 통화의 녹음만 포기한다. path={}", message, spoolPath, cause);
    }

    private void closeQuietly() {
        try {
            spool.close();
        } catch (IOException e) {
            log.warn("[Recording] 스풀 파일 닫기 실패. path={}", spoolPath, e);
        }
    }

    private void deleteSpool() {
        try {
            Files.deleteIfExists(spoolPath);
        } catch (IOException e) {
            log.warn("[Recording] 스풀 파일 삭제 실패. path={}", spoolPath, e);
        }
    }

    /**
     * 한 소스(사용자 / AI)가 타임라인에서 <b>어디까지 소리를 채웠는지</b>. 다음 덩어리는 "지금"과
     * "직전 소리 끝" 중 <b>늦은 쪽</b>에 놓는다.
     *
     * <p>둘 중 하나만 쓰면 각각 깨진다: 늘 "지금"이면 몰아서 도착한 조각들이 같은 자리에 겹쳐 뭉개지고,
     * 늘 "직전 끝"이면 말이 없던 시간이 사라져 이어붙이기(concat)와 같아진다.
     */
    private final class Track {

        private long cursorFrame;

        private void append(short[] samples) throws IOException {
            long startFrame = Math.max(nowFrame(), cursorFrame);
            mix(startFrame, samples);
            cursorFrame = startFrame + samples.length;
        }

        /** 커서를 지워 다음 소리가 "지금"부터 놓이게 한다(끼어들기). */
        private void rewind() {
            cursorFrame = 0;
        }
    }
}
