package com.example.umcCall.domain.call.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 문장 분할 경계를 고정하는 테스트. 여기가 흔들리면 TTS 호출 수·운율·TTFA가 같이 흔들린다.
 */
class SentenceBufferTest {

    @Test
    void 문장이_완성되면_바로_내보낸다() {
        SentenceBuffer buffer = new SentenceBuffer();

        // 첫 문장이 완성되는 순간 = TTS를 시작할 수 있는 순간. 이게 TTFA 단축의 전부다.
        assertThat(buffer.feed("응, 나 방금 ")).isEmpty();
        assertThat(buffer.feed("퇴근했어. 너는")).containsExactly("응, 나 방금 퇴근했어.");
    }

    @Test
    void 조각_하나에_문장이_여럿이면_모두_내보낸다() {
        SentenceBuffer buffer = new SentenceBuffer();

        assertThat(buffer.feed("오늘 진짜 힘들었어. 너 보고 싶었다니까! 지금 뭐 하고 있어?"))
                .containsExactly("오늘 진짜 힘들었어.", "너 보고 싶었다니까!", "지금 뭐 하고 있어?");
    }

    @Test
    void 짧은_토막은_다음_문장과_합쳐서_내보낸다() {
        SentenceBuffer buffer = new SentenceBuffer();

        // "응!" 하나로 TTS를 부르면 호출만 늘고 말이 뚝뚝 끊긴다.
        assertThat(buffer.feed("응! 나 방금 퇴근했어.")).containsExactly("응! 나 방금 퇴근했어.");
    }

    @Test
    void 마지막_문장은_flush로_나온다() {
        SentenceBuffer buffer = new SentenceBuffer();
        buffer.feed("응, 나 방금 퇴근했어. 너는 뭐 해");

        // 종결 부호 없이 스트림이 끝나는 경우 — 꼬리를 버리면 대사가 잘린 채 나간다.
        assertThat(buffer.flush()).contains("너는 뭐 해");
        assertThat(buffer.flush()).isEmpty(); // 두 번 불러도 중복 발화가 없어야 한다
    }

    @Test
    void 개행도_문장_경계로_본다() {
        SentenceBuffer buffer = new SentenceBuffer();

        assertThat(buffer.feed("오늘은 좀 피곤하네\n그래도 목소리 들으니 좋다"))
                .containsExactly("오늘은 좀 피곤하네");
    }

    @Test
    void 종결_부호가_없어도_최대_길이에서_잘린다() {
        SentenceBuffer buffer = new SentenceBuffer();

        // 부호 없이 계속 이어지는 응답에 갇히면 첫 소리가 영영 안 나간다.
        List<String> sentences = buffer.feed("가나다라마바사아자차 ".repeat(20));

        assertThat(sentences).isNotEmpty();
        assertThat(sentences.get(0).length()).isLessThanOrEqualTo(120);
    }

    @Test
    void 소수점은_문장_끝으로_보지_않는다() {
        SentenceBuffer buffer = new SentenceBuffer();

        // 버퍼 끝의 "3." 을 문장 끝으로 확정하면 다음 조각의 "5"가 새 문장이 되어 "3." / "5도가.." 로 갈린다.
        assertThat(buffer.feed("지금 기온이 영하 3.")).isEmpty();
        assertThat(buffer.feed("5도래. 진짜 춥지")).containsExactly("지금 기온이 영하 3.5도래.");
    }

    @Test
    void 종결_부호_뒤의_닫는_따옴표는_앞_문장에_붙는다() {
        SentenceBuffer buffer = new SentenceBuffer();

        assertThat(buffer.feed("그래서 내가 \"먼저 자!\" 라고 했잖아."))
                .containsExactly("그래서 내가 \"먼저 자!\"");
        // 남은 "라고 했잖아."는 최소 길이 미만이라 다음 문장을 기다리다 flush로 나온다.
        assertThat(buffer.flush()).contains("라고 했잖아.");
    }

    @Test
    void 빈_조각은_무시한다() {
        SentenceBuffer buffer = new SentenceBuffer();

        assertThat(buffer.feed(null)).isEmpty();
        assertThat(buffer.feed("")).isEmpty();
    }
}
