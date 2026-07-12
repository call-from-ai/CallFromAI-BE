package com.example.umcCall.domain.call.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** CLOVA {@code recognize} 응답 파싱 및 partial/final 판정 검증. (실제 응답 예시 기준) */
class NestRecognizeResultTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void epdType이_채워진_전사는_final로_판정한다() throws Exception {
        // 실제 CLOVA 응답 예시 (epdType=durationThreshold → EPD가 끊은 확정 구간)
        String contents = """
                {
                  "uid": "abc",
                  "responseType": [ "transcription" ],
                  "transcription": {
                    "text": "입니다.",
                    "position": 0,
                    "epFlag": false,
                    "seqId": 0,
                    "epdType": "durationThreshold",
                    "startTimestamp": 190,
                    "endTimestamp": 840,
                    "confidence": 0.997389124199423,
                    "alignInfos": [
                      {"word":"입","start":190,"end":340,"confidence":0.99}
                    ]
                  }
                }
                """;

        NestRecognizeResult result = objectMapper.readValue(contents, NestRecognizeResult.class);

        assertThat(result.isTranscription()).isTrue();
        assertThat(result.isFinal()).isTrue();
        assertThat(result.text()).isEqualTo("입니다.");
    }

    @Test
    void epdType이_빈_전사는_partial로_판정한다() throws Exception {
        // 말하는 도중의 중간 결과: epdType이 빈 값
        String contents = """
                {
                  "responseType": [ "transcription" ],
                  "transcription": {
                    "text": "안녕하",
                    "epdType": "",
                    "epFlag": false
                  }
                }
                """;

        NestRecognizeResult result = objectMapper.readValue(contents, NestRecognizeResult.class);

        assertThat(result.isTranscription()).isTrue();
        assertThat(result.isFinal()).isFalse();
        assertThat(result.text()).isEqualTo("안녕하");
    }

    @Test
    void 전사가_아닌_응답은_transcription이_아니다() throws Exception {
        // responseType에 transcription이 없는 경우 (예: config 확인 응답)
        String contents = """
                { "responseType": [ "config" ] }
                """;

        NestRecognizeResult result = objectMapper.readValue(contents, NestRecognizeResult.class);

        assertThat(result.isTranscription()).isFalse();
        assertThat(result.isFinal()).isFalse();
    }

    @Test
    void 예상치_못한_필드가_있어도_파싱된다() throws Exception {
        // alignInfos, periodPositions 등 우리가 안 쓰는 필드가 있어도 깨지지 않아야 한다.
        String contents = """
                {
                  "responseType": [ "transcription" ],
                  "transcription": {
                    "text": "테스트",
                    "epdType": "endPoint",
                    "periodPositions": [3],
                    "alignInfos": []
                  },
                  "somethingNew": 123
                }
                """;

        NestRecognizeResult result = objectMapper.readValue(contents, NestRecognizeResult.class);

        assertThat(result.isFinal()).isTrue();
        assertThat(result.text()).isEqualTo("테스트");
    }
}
