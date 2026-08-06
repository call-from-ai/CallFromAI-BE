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
 * {@code callTime}과 크게 어긋나고, 사용자 발화 구간을 잘라낼 근거(STT 타임스탬프)가 필요해지며,
 * 끼어들기가 겹치는 순간을 표현할 수 없다.
 *
 * <p><b>왜 랜덤 액세스인가</b>: AI 대사가 사용자의 침묵 구간보다 길면 <b>이미 써둔 뒤쪽에 겹쳐 써야</b> 한다.
 * 앞에서 뒤로만 흐르는 스트리밍 인코더로는 구조적으로 불가능하다. (어느 위치에 얹는지는 {@link #append})
 *
 * <p>⚠ 버퍼가 <b>메모리</b>인 건 통화 상한이 5분(통화당 최대 ~10MB)이기 때문이다 —
 * <b>동시 통화가 두 자릿수 후반으로 가면 파일로 되돌려야 한다</b>(힙이 통화 수에 비례한다).
 *
 * <p>fail-open이다 — 깨진 조각은 건너뛰고 통화·녹음 모두 계속한다.
 * 스레드 안전하다: 업스트림은 WS 수신 스레드, AI는 통화 워커가 쓴다.
 */
@Slf4j
public final class CallRecorder {

    /** 녹음 기준 샘플레이트. 사용자 업스트림(16kHz)을 원본 그대로 두고 AI(Typecast 44.1kHz)를 여기 맞춘다. */
    public static final int SAMPLE_RATE = 16_000;

    private static final int FRAMES_PER_MS = SAMPLE_RATE / 1000;

    /**
     * 버퍼 상한(= 5분). {@code call.timeout.max-call-minutes}와 같은 값이지만 <b>일부러 상수로 둔다</b> —
     * 스위퍼가 못 돌아도 힙이 무한정 늘지 않게 막는 마지막 방어선이라, 그 설정에 기대면 의미가 없다.
     */
    private static final int MAX_FRAMES = 5 * 60 * SAMPLE_RATE;

    /** 첫 할당(30초). 짧은 통화가 대부분이라 작게 잡고 필요하면 늘린다. */
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
     * AI 대사 wav 한 조각(Typecast 44.1kHz)을 16kHz로 낮춰 타임라인에 얹는다.
     * <p>레이트는 <b>wav 헤더에서 읽는다</b> — 그래서 TTS 벤더가 바뀌어도(2026-08-06 CLOVA 24kHz →
     * Typecast 44.1kHz) 이 메서드는 손댈 게 없었다. 상수로 박아뒀다면 조용히 배속 재생이 됐을 자리다.
     * <p>한 턴이 문장 단위로 <b>여러 조각</b>이라(#117) 커서가 조각을 이어 붙인다 —
     * 클라이언트가 큐에 넣어 순서대로 재생하는 모습과 같은 배치가 된다.
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
     * 끼어들기로 클라이언트가 재생 큐를 버렸다 — <b>아직 들리지 않은 AI 음성을 잘라내고</b>
     * AI 커서를 "지금"으로 되돌린다.
     *
     * <p><b>왜 잘라내야 하는가</b>: TTS wav는 재생 속도보다 훨씬 빨리 도착해(실측: 24초 분량이 0.26초에)
     * {@code aiCursor}가 "지금"보다 수십 초 앞서 있다. 커서만 되돌리고 그 소리를 남겨두면, 다음 대사가
     * {@code max(지금, 0)} = 지금에 얹히는데 거기엔 <b>버려진 이전 대사가 깔려 있어</b>
     * ({@link #mixAt}은 덮어쓰기가 아니라 더하기다) 녹음에서 <b>AI 목소리 둘이 겹쳐</b> 들린다.
     *
     * <p><b>왜 잘라도 안전한가</b>: 사용자 오디오는 실시간으로 도착하므로 {@code userCursor}가 "지금"을
     * 앞서지 않는다 — 즉 그 뒤 구간은 <b>전부 AI 음성뿐</b>이다. 그래도 한 프레임 차이로 사용자 소리를
     * 자르지 않도록 {@code userCursor}와 늦은 쪽을 기준으로 삼는다.
     *
     * <p>⚠ 예전엔 "이미 써넣은 소리는 지우지 않는다 — 사용자가 어디까지 들었는지 서버가 알 수 없다"였다.
     * 그 전제가 실제와 달랐다: 녹음 타임라인은 <b>벽시계</b>이고 클라이언트는 <b>실시간</b>으로 재생하므로,
     * 여기서의 "지금" ≈ 클라이언트의 재생 지점이다. (#139에서 끼어들기가 실제로 동작하기 시작하며 드러났다 —
     * 그전까지 이 메서드는 불린 적이 없다.)
     *
     * <p>⚠ 그래도 <b>끼어들기 직전 수백 ms는 여전히 겹친다</b> — 네트워크·재생 지연만큼 클라이언트의 재생
     * 지점이 서버의 "지금"보다 뒤처지기 때문이다. 완전히 없애려면 클라이언트가 재생 위치를 보고해야 하는데
     * 그건 WS 계약이 늘어나는 일이라 하지 않았다.
     */
    public synchronized void resetAiCursor() {
        long cut = Math.max(nowFrame(), userCursor);
        if (length > cut) {
            Arrays.fill(mix, (int) cut, length, (short) 0);
            length = (int) cut;
        }
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

    /** 두 배씩 늘린다. 늘어난 구간은 0 = 무음이라 침묵이 저절로 채워진다. */
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

    /** 녹음 원점부터 지금까지의 프레임(=샘플) 수. */
    private long nowFrame() {
        return (nanoClock.getAsLong() - startedAtNanos) / 1_000_000L * FRAMES_PER_MS;
    }

    /** 리틀엔디언 raw PCM 바이트를 샘플로 편다. */
    private static short[] toSamples(byte[] pcm) {
        return WavCodec.toSamples(ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN), 0, pcm.length);
    }
}
