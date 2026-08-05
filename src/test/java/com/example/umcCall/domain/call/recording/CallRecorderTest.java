package com.example.umcCall.domain.call.recording;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * 타임라인 믹스의 배치 규칙을 고정하는 테스트. 여기가 흔들리면 녹음에서 말이 겹치거나,
 * 침묵이 사라져 길이가 실제 통화와 어긋난다.
 *
 * <p>시계를 주입해 "언제 도착했는지"를 직접 조작한다 — 배치가 전부 시각에 달려 있어서
 * 실시간에 기대면 검증이 불가능하다.
 */
class CallRecorderTest {

    /** 1ms = 16 프레임(16kHz 모노). 테스트에서 시각을 프레임으로 환산할 때 쓴다. */
    private static final int FRAMES_PER_MS = 16;

    private final AtomicLong nanos = new AtomicLong();
    private final CallRecorder recorder = new CallRecorder(nanos::get);

    @Test
    void 몰아서_도착한_프레임도_겹치지_않고_이어붙는다() {
        // 프론트는 CALL_READY 전에 버퍼링한 프레임을 한꺼번에 flush한다(3장 계약).
        // 도착 시각에 그대로 얹으면 이 구간이 통째로 겹쳐 첫 마디가 뭉개진다.
        recorder.writeUpstream(pcm((short) 1, (short) 2));
        recorder.writeUpstream(pcm((short) 3, (short) 4));

        assertThat(samples()).containsExactly((short) 1, (short) 2, (short) 3, (short) 4);
    }

    @Test
    void 말이_끊긴_구간은_무음으로_남는다() {
        recorder.writeUpstream(pcm((short) 1, (short) 2));

        elapse(100);
        recorder.writeUpstream(pcm((short) 3, (short) 4));

        // 침묵이 사라지면 그게 곧 concat이다 — 길이가 callTime과 어긋나고 AI 위치도 전부 밀린다.
        short[] samples = samples();
        int gapStart = 100 * FRAMES_PER_MS;
        assertThat(samples).hasSize(gapStart + 2);
        assertThat(samples[2]).isZero();
        assertThat(samples[gapStart]).isEqualTo((short) 3);
    }

    @Test
    void AI_음성은_24kHz에서_16kHz로_변환돼_들어간다() {
        short[] ramp = new short[30];
        for (int i = 0; i < ramp.length; i++) {
            ramp[i] = (short) (i * 100);
        }
        recorder.writeAiWav(wav(24_000, ramp));

        // 24k 30샘플 = 16k 20샘플. 값은 선형 보간이라 램프 기울기가 1.5배가 된다.
        short[] samples = samples();
        assertThat(samples).hasSize(20);
        assertThat(samples[1]).isEqualTo((short) 150);
        assertThat(samples[2]).isEqualTo((short) 300);
    }

    @Test
    void AI_문장이_연달아_와도_겹치지_않고_이어진다() {
        // 한 턴이 문장 단위 wav N조각으로 쪼개져(#117) 재생 속도보다 빨리 도착한다.
        recorder.writeAiWav(wav(16_000, (short) 10, (short) 20));
        recorder.writeAiWav(wav(16_000, (short) 30, (short) 40));

        assertThat(samples()).containsExactly((short) 10, (short) 20, (short) 30, (short) 40);
    }

    @Test
    void 사용자와_AI가_동시에_말하면_두_소리가_섞인다() {
        // 끼어들기 구간 = 두 목소리가 같은 시각에 있는 구간. 덮어쓰면 한쪽이 통째로 사라진다.
        recorder.writeUpstream(pcm((short) 100, (short) 100));
        recorder.writeAiWav(wav(16_000, (short) 50, (short) 50));

        assertThat(samples()).containsExactly((short) 150, (short) 150);
    }

    @Test
    void 더한_값이_한계를_넘으면_잘라낸다() {
        recorder.writeUpstream(pcm((short) 32_000));
        recorder.writeAiWav(wav(16_000, (short) 32_000));

        // 넘긴 채 두면 부호가 뒤집혀 '딱' 하는 잡음이 된다.
        assertThat(samples()).containsExactly(Short.MAX_VALUE);
    }

    @Test
    void 끼어들기_뒤의_AI_음성은_현재_시각부터_기록된다() {
        short[] longSpeech = new short[3200]; // 200ms 분량
        Arrays.fill(longSpeech, (short) 1000);
        recorder.writeAiWav(wav(16_000, longSpeech));

        elapse(50);
        recorder.resetAiCursor(); // 클라이언트가 재생 큐를 버렸다
        recorder.writeAiWav(wav(16_000, (short) 7));

        // 커서를 그대로 두면 다음 대사가 150ms 뒤로 밀려, 녹음에서만 AI가 늦게 대답하는 것처럼 들린다.
        // ⚠ 값이 1007이 아니라 7인 건 아직 들리지 않은 잔여 음성(1000)이 잘려나갔기 때문이다
        //   — 아래 테스트가 그 규칙을 따로 고정한다.
        assertThat(samples()[50 * FRAMES_PER_MS]).isEqualTo((short) 7);
    }

    @Test
    void 끼어들면_아직_들리지_않은_AI_음성은_잘려나간다() {
        // ⚠ TTS wav는 재생 속도보다 훨씬 빨리 도착해(실측: 24초 분량이 0.26초에) 커서가 "지금"보다
        //   수십 초 앞선다. 이걸 남겨두면 다음 대사가 그 위에 <b>더해져</b>(mixAt은 덮어쓰기가 아니다)
        //   녹음에서 AI 목소리 둘이 겹쳐 들린다.
        short[] longSpeech = new short[3200]; // 200ms 분량
        Arrays.fill(longSpeech, (short) 1000);
        recorder.writeAiWav(wav(16_000, longSpeech));

        elapse(50);          // 사용자는 50ms까지만 들었다
        recorder.resetAiCursor();

        // 들린 만큼만 남는다 — 뒤쪽 150ms는 사라진다.
        assertThat(samples()).hasSize(50 * FRAMES_PER_MS);
        assertThat(samples()[50 * FRAMES_PER_MS - 1]).isEqualTo((short) 1000);
    }

    @Test
    void 끼어들어도_사용자_소리는_자르지_않는다() {
        // ⚠ 잘라내는 기준이 "지금"뿐이면, 바로 직전에 도착한 사용자 프레임이 한 프레임 차이로 날아간다.
        //   사용자 발화는 끼어들기의 <b>원인</b>이라 녹음에 반드시 남아야 한다.
        short[] longSpeech = new short[3200];
        Arrays.fill(longSpeech, (short) 1000);
        recorder.writeAiWav(wav(16_000, longSpeech));

        elapse(50);
        recorder.writeUpstream(pcm((short) 500, (short) 500));   // 끼어드는 목소리
        recorder.resetAiCursor();

        assertThat(samples()).hasSize(50 * FRAMES_PER_MS + 2);
        // AI(1000) + 사용자(500)가 섞인 채 살아 있다.
        assertThat(samples()[50 * FRAMES_PER_MS]).isEqualTo((short) 1500);
    }

    @Test
    void 마감하면_재생_가능한_wav가_된다() {
        recorder.writeUpstream(pcm((short) 1, (short) 2));

        byte[] wav = recorder.finish().orElseThrow();

        assertThat(wav).hasSize(WavCodec.HEADER_BYTES + 4); // 헤더 + 샘플 2개
        assertThat(WavCodec.decode(wav).sampleRate()).isEqualTo(CallRecorder.SAMPLE_RATE);
    }

    @Test
    void 소리가_하나도_없으면_빈_값이다() {
        // 연결만 되고 끝난 통화 — 빈 녹음이 S3로 올라가지 않게.
        assertThat(recorder.finish()).isEmpty();
    }

    @Test
    void 깨진_AI_조각은_건너뛰고_녹음은_계속된다() {
        recorder.writeUpstream(pcm((short) 1, (short) 2));

        // fail-open: 조각 하나가 깨져도 통화도 녹음도 멈추지 않는다.
        assertThatCode(() -> recorder.writeAiWav("이건 wav가 아니다".getBytes()))
                .doesNotThrowAnyException();

        assertThat(samples()).containsExactly((short) 1, (short) 2);
    }

    @Test
    void 상한을_넘긴_오디오는_버린다() {
        // 스위퍼가 못 돌아도 힙이 무한정 늘지 않아야 한다 — 마지막 방어선.
        elapse(6 * 60 * 1000); // 상한(5분)을 넘긴 시각
        recorder.writeUpstream(pcm((short) 1, (short) 2));

        assertThat(recorder.finish()).isEmpty();
    }

    /** 시계를 ms만큼 앞으로 돌린다(= 그동안 아무 소리도 도착하지 않았다). */
    private void elapse(long ms) {
        nanos.addAndGet(ms * 1_000_000L);
    }

    /** 마감한 녹음의 샘플 전체. */
    private short[] samples() {
        return WavCodec.decode(recorder.finish().orElseThrow()).samples();
    }

    private static byte[] pcm(short... samples) {
        ByteBuffer buffer = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : samples) {
            buffer.putShort(sample);
        }
        return buffer.array();
    }

    private static byte[] wav(int sampleRate, short... samples) {
        byte[] header = WavCodec.header(samples.length * 2, sampleRate);
        byte[] data = pcm(samples);
        return ByteBuffer.allocate(header.length + data.length).put(header).put(data).array();
    }
}
