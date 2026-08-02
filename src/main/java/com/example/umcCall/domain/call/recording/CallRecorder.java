package com.example.umcCall.domain.call.recording;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

/**
 * 통화 한 건의 오디오를 <b>타임라인 믹스</b>로 모은다. 사용자 업스트림 PCM과 AI 다운스트림 wav를 한 버퍼에
 * 얹어, 통화가 끝나면 그대로 재생 가능한 wav 바이트가 된다(다시듣기).
 *
 * <p><b>왜 이어붙이기(concat)가 아니라 믹스인가</b>: 이어붙이면 침묵·생각하는 시간이 통째로 빠져 길이가
 * {@code callTime}과 크게 어긋나고, 사용자 발화 구간을 잘라낼 근거(STT 타임스탬프)가 필요해지며, 끼어들기가
 * 겹치는 순간을 표현할 수 없다. 믹스는 업스트림을 통째로 담으므로 절단이 아예 불필요하다.
 *
 * <p><b>왜 랜덤 액세스인가</b>: AI 대사가 사용자의 침묵 구간보다 길면 <b>이미 써둔 뒤쪽에 겹쳐 써야</b> 한다.
 * 앞에서 뒤로만 흐르는 스트리밍 인코더로는 구조적으로 불가능하다. 아직 안 쓴 구간은 0(무음)이라 침묵이 공짜다.
 *
 * <p>⚠ <b>메모리 배열이다.</b> 통화 상한이 {@code call.timeout.max-call-minutes}(5분)라 통화당 최대 ~10MB고,
 * 크래시로 남은 녹음은 어차피 버리기로 했으므로 디스크에 둘 이유가 없었다(파일 생성·고아 정리·디스크 실패
 * 처리가 통째로 사라진다). ⚠ <b>동시 통화가 두 자릿수 후반으로 가면 되돌려야 한다</b> — 그때는 힙이 통화 수에
 * 비례해 커진다.
 *
 * <p><b>트랙마다 커서가 있다.</b> 도착 시각에 그대로 얹지 않는 이유는 두 소스 모두 <b>실시간보다 빠르게
 * 도착하는 구간</b>이 있어서다 — 프론트는 {@code CALL_READY} 전에 버퍼링한 프레임을 한꺼번에 flush하고,
 * TTS는 문장 wav를 재생 속도보다 빨리 뱉는다(#117). 도착 시각에 얹으면 그 구간이 통째로 겹쳐 뭉개진다.
 * 커서는 "직전 소리가 끝난 지점"과 "지금"의 <b>늦은 쪽</b>을 골라 이걸 막는다.
 *
 * <p><b>fail-open이다.</b> 녹음 실패가 통화·전사에 영향을 주면 안 되므로 깨진 조각은 건너뛰고 로그만 남긴다.
 *
 * <p>스레드 안전하다 — 업스트림은 WS 수신 스레드, AI는 통화 워커가 쓰므로 접근을 통째로 직렬화한다.
 */
@Slf4j
public final class CallRecorder {

    /** 녹음 기준 샘플레이트. 사용자 업스트림(16kHz)을 원본 그대로 두고 AI(24kHz)를 여기 맞춘다. */
    public static final int SAMPLE_RATE = 16_000;

    private static final int FRAMES_PER_MS = SAMPLE_RATE / 1000;

    /**
     * 버퍼 상한(= 5분). {@code call.timeout.max-call-minutes}와 같은 값이지만 <b>일부러 상수로 둔다</b> —
     * 스위퍼가 못 돌아도 힙이 무한정 늘지 않게 막는 마지막 방어선이라, 그 설정에 기대면 의미가 없다.
     */
    private static final int MAX_FRAMES = 5 * 60 * SAMPLE_RATE;

    /** 첫 할당(30초). 짧은 통화가 대부분이라 작게 잡고 필요할 때 두 배씩 늘린다. */
    private static final int INITIAL_FRAMES = 30 * SAMPLE_RATE;

    private final LongSupplier nanoClock;
    private final long startedAtNanos;

    private short[] mix = new short[INITIAL_FRAMES];
    private int length;
    private long userCursor;
    private long aiCursor;

    public CallRecorder() {
        this(System::nanoTime);
    }

    CallRecorder(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
        this.startedAtNanos = nanoClock.getAsLong();
    }

    /** 사용자 마이크 프레임(16kHz 모노 raw PCM)을 타임라인에 얹는다. */
    public synchronized void writeUpstream(byte[] pcm) {
        userCursor = append(userCursor, toSamples(pcm));
    }

    /**
     * AI 대사 wav 한 조각(CLOVA Voice 24kHz)을 16kHz로 낮춰 타임라인에 얹는다.
     * <p>한 턴이 문장 단위로 <b>여러 조각</b>이라(#117) 커서가 조각을 이어 붙인다 — 클라이언트가
     * 큐에 넣어 순서대로 재생하는 모습과 같은 배치가 된다.
     */
    public synchronized void writeAiWav(byte[] wav) {
        try {
            aiCursor = append(aiCursor, WavCodec.decode(wav).resampleTo(SAMPLE_RATE).samples());
        } catch (RuntimeException e) {
            // 조각 하나가 깨져도 통화도 녹음도 계속한다 — 그 조각만 빠진다.
            log.error("[Recording] AI 오디오 조각을 읽지 못해 건너뛴다. bytes={}", wav.length, e);
        }
    }

    /**
     * 끼어들기로 클라이언트가 재생 큐를 버렸다 — AI 커서를 "지금"으로 되돌린다.
     * <p>커서를 그대로 두면 <b>버려진 소리가 차지하던 시간만큼</b> 다음 대사가 뒤로 밀려, 녹음에서만
     * AI가 몇 초씩 늦게 대답하는 것처럼 들린다. 이미 써넣은 소리는 <b>지우지 않는다</b> — 서버가 실제로
     * 내보낸 것이고, 사용자가 어디까지 들었는지는 서버가 알 수 없다.
     */
    public synchronized void resetAiCursor() {
        aiCursor = 0;
    }

    /**
     * 녹음을 마감해 재생 가능한 wav 바이트로 만든다.
     *
     * @return 완성된 wav. 소리가 한 조각도 안 들어왔으면 <b>빈 값</b>(연결만 되고 끝난 통화 등).
     */
    public synchronized Optional<byte[]> finish() {
        if (length == 0) {
            return Optional.empty();
        }
        ByteBuffer wav = ByteBuffer.allocate(WavCodec.HEADER_BYTES + length * 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(WavCodec.header(length * 2, SAMPLE_RATE));
        for (int i = 0; i < length; i++) {
            wav.putShort(mix[i]);
        }
        return Optional.of(wav.array());
    }

    /**
     * 소리 한 덩어리를 "지금"과 "직전 소리 끝" 중 늦은 쪽에 얹고, 새 커서를 돌려준다.
     * <p>둘 중 하나만 쓰면 각각 깨진다: 늘 "지금"이면 몰아서 도착한 조각이 같은 자리에 겹쳐 뭉개지고,
     * 늘 "직전 끝"이면 말이 없던 시간이 사라져 이어붙이기(concat)와 같아진다.
     */
    private long append(long cursor, short[] samples) {
        long start = Math.max(nowFrame(), cursor);
        mixAt(start, samples);
        return start + samples.length;
    }

    /** {@code start} 위치에 <b>더한다</b>(덮어쓰기가 아니다) — 두 사람이 동시에 말한 구간이 섞이도록. */
    private void mixAt(long start, short[] samples) {
        if (start >= MAX_FRAMES) {
            return;
        }
        int count = (int) Math.min(samples.length, MAX_FRAMES - start);
        int from = (int) start;
        ensureCapacity(from + count);
        for (int i = 0; i < count; i++) {
            // 더한 값이 16-bit를 넘으면 잘라낸다 — 넘긴 채 두면 부호가 뒤집혀 '딱' 하는 잡음이 된다.
            int sum = mix[from + i] + samples[i];
            mix[from + i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sum));
        }
        length = Math.max(length, from + count);
    }

    /** 필요한 만큼 두 배씩 늘린다. 늘어난 구간은 0 = 무음이라 침묵이 따로 표현할 필요 없이 채워진다. */
    private void ensureCapacity(int frames) {
        if (frames <= mix.length) {
            return;
        }
        int capacity = mix.length;
        while (capacity < frames) {
            capacity = Math.min(capacity * 2, MAX_FRAMES);
        }
        mix = Arrays.copyOf(mix, capacity);
    }

    /** 녹음 원점부터 지금까지의 프레임(=샘플) 수. 타임라인 위치의 기준이다. */
    private long nowFrame() {
        return (nanoClock.getAsLong() - startedAtNanos) / 1_000_000L * FRAMES_PER_MS;
    }

    /** 리틀엔디언 raw PCM 바이트를 샘플로 편다. */
    private static short[] toSamples(byte[] pcm) {
        return WavCodec.toSamples(ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN), 0, pcm.length);
    }
}
