package com.example.umcCall.domain.call.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

/**
 * 발화 시작 감지의 <b>불변식을 고정</b>하는 테스트.
 * 여기가 느슨해지면 잡음에 AI가 끊기거나(임계·히스테리시스), 끼어들어도 안 멈춘다(#139).
 */
class CallVadTest {

    private static final int THRESHOLD = 1_200;
    private static final int MIN_SPEECH_MS = 60;
    /** 20ms @16kHz. 프론트가 보내는 프레임 크기와 같다. */
    private static final int FRAME_SAMPLES = 320;

    private CallVad vad() {
        return new CallVad(THRESHOLD, MIN_SPEECH_MS);
    }

    /** 진폭이 일정한 20ms 프레임. */
    private static byte[] frame(int amplitude) {
        return frame(amplitude, FRAME_SAMPLES);
    }

    private static byte[] frame(int amplitude, int samples) {
        ByteBuffer buffer = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < samples; i++) {
            // 부호를 번갈아 준다 — 평균이 0인 실제 파형에 가깝고, RMS는 진폭 그대로 나온다.
            buffer.putShort((short) (i % 2 == 0 ? amplitude : -amplitude));
        }
        return buffer.array();
    }

    @Test
    void 임계를_넘는_소리가_최소시간만큼_이어지면_발화_시작이다() {
        CallVad vad = vad();
        byte[] loud = frame(5_000);

        // 20ms + 20ms 로는 아직 60ms에 못 미친다.
        assertThat(vad.isSpeechStart(loud)).isFalse();
        assertThat(vad.isSpeechStart(loud)).isFalse();
        // 3번째 프레임에서 60ms 도달.
        assertThat(vad.isSpeechStart(loud)).isTrue();
    }

    @Test
    void 임계_미만이면_아무리_이어져도_발화가_아니다() {
        CallVad vad = vad();
        byte[] quiet = frame(THRESHOLD - 1);

        for (int i = 0; i < 50; i++) {   // 1초어치
            assertThat(vad.isSpeechStart(quiet)).isFalse();
        }
    }

    @Test
    void 중간에_조용해지면_누적이_초기화된다() {
        // ⚠ 이게 없으면 띄엄띄엄한 잡음이 쌓여 발화로 오인된다.
        CallVad vad = vad();
        byte[] loud = frame(5_000);
        byte[] quiet = frame(0);

        assertThat(vad.isSpeechStart(loud)).isFalse();   // 20ms
        assertThat(vad.isSpeechStart(loud)).isFalse();   // 40ms
        assertThat(vad.isSpeechStart(quiet)).isFalse();  // ← 초기화
        assertThat(vad.isSpeechStart(loud)).isFalse();   // 다시 20ms
        assertThat(vad.isSpeechStart(loud)).isFalse();   // 40ms
        assertThat(vad.isSpeechStart(loud)).isTrue();    // 60ms
    }

    @Test
    void 말하는_내내_계속_true를_내지_않는다() {
        // 발화 하나에 한 번만. 매 프레임 알리면 호출 비용과 로그가 낭비된다.
        CallVad vad = vad();
        byte[] loud = frame(5_000);

        vad.isSpeechStart(loud);
        vad.isSpeechStart(loud);
        assertThat(vad.isSpeechStart(loud)).isTrue();

        for (int i = 0; i < 50; i++) {
            assertThat(vad.isSpeechStart(loud)).isFalse();
        }
    }

    @Test
    void 조용해진_뒤_다시_말하면_또_감지한다() {
        // ⚠ 한 통화에서 끼어들기는 여러 번 일어난다 — 한 번 쓰고 죽으면 두 번째부터 안 멈춘다.
        CallVad vad = vad();
        byte[] loud = frame(5_000);
        byte[] quiet = frame(0);

        vad.isSpeechStart(loud);
        vad.isSpeechStart(loud);
        assertThat(vad.isSpeechStart(loud)).isTrue();

        assertThat(vad.isSpeechStart(quiet)).isFalse();   // 발화 끝

        vad.isSpeechStart(loud);
        vad.isSpeechStart(loud);
        assertThat(vad.isSpeechStart(loud)).isTrue();      // 두 번째 발화도 감지
    }

    @Test
    void 큰_프레임_하나로도_최소시간을_채울_수_있다() {
        // 프레임 크기는 프론트가 정한다 — 20ms 고정이라고 가정하면 안 된다.
        CallVad vad = vad();
        byte[] oneShot = frame(5_000, FRAME_SAMPLES * 5);   // 100ms

        assertThat(vad.isSpeechStart(oneShot)).isTrue();
    }

    @Test
    void 아주_작은_프레임도_이어지면_최소시간을_채운다() {
        // ⚠ 회귀 방어(PR #142 리뷰 지적): 프레임마다 ms로 환산해 더하면 1ms(=16샘플) 미만 프레임은
        // 환산값이 0이라 아무리 말해도 누적이 안 늘고 끼어들기가 통째로 죽는다.
        CallVad vad = vad();
        byte[] tiny = frame(5_000, 8);                     // 0.5ms — 환산하면 0이 되는 크기
        int framesToFill = MIN_SPEECH_MS * 16 / 8;         // 60ms = 960샘플 → 8샘플 프레임 120개

        for (int i = 0; i < framesToFill - 1; i++) {
            assertThat(vad.isSpeechStart(tiny)).isFalse();
        }
        assertThat(vad.isSpeechStart(tiny)).isTrue();
    }

    @Test
    void 빈_프레임은_상태를_건드리지_않는다() {
        // 빈 프레임에 초기화까지 해버리면 정상 발화가 끊겨 감지가 늦어진다.
        CallVad vad = vad();
        byte[] loud = frame(5_000);

        assertThat(vad.isSpeechStart(loud)).isFalse();
        assertThat(vad.isSpeechStart(loud)).isFalse();
        assertThat(vad.isSpeechStart(new byte[0])).isFalse();
        assertThat(vad.isSpeechStart(loud)).isTrue();       // 누적이 유지됐다
    }
}
