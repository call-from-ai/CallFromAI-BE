package com.example.umcCall.domain.call.client;

import com.example.umcCall.global.apiPayload.code.GeneralErrorCode;
import com.example.umcCall.global.exception.BaseException;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Typecast(TTS) 배관. 텍스트 한 덩이를 보내면 합성된 wav 바이트가 한 번에 돌아온다.
 * (스트리밍 아님 — 지연 완화는 호출부에서 문장 단위로 쪼개 여러 번 부르는 방식)
 * 반환한 wav는 헤더까지 그대로다. 서버는 변환하지 않고 프론트가 헤더로 스펙을 읽는다.
 *
 * <p><b>스트리밍 엔드포인트를 쓰지 않는 이유</b>: 우리 파이프라인은 이미 LLM SSE를 문장으로 잘라
 * 문장마다 이 클라이언트를 부른다(#117). 즉 조각 단위 전달은 호출부가 이미 하고 있고, TTS까지
 * 스트리밍으로 바꾸면 "조각 하나 = 완결 wav 하나"라는 다운스트림 계약(방식 A)이 깨진다.
 * 게다가 Typecast 스트리밍 wav는 32kHz라 표준(44.1kHz)과 샘플레이트도 다르다.
 */
@Slf4j
@Component
public class TypecastVoiceClient {

    private final RestClient restClient;
    private final TypecastVoiceProperties properties;

    public TypecastVoiceClient(RestClient.Builder builder, TypecastVoiceProperties properties) {
        this.properties = properties;
        // 타임아웃을 명시해 외부 API 지연 시 합성 워커가 무한정 막히지 않게 한다.
        // (연결·응답 상한을 넘으면 ResourceAccessException → synthesize의 RestClientException catch로 흐른다)
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                // ⚠ Bearer가 아니다 — Typecast는 키 값을 그대로 싣는다.
                .defaultHeader("X-API-KEY", properties.apiKey())
                .build();
    }

    /**
     * 텍스트를 {@code voiceId} 목소리로 합성해 wav 바이트를 돌려준다. 실패 시 {@link BaseException}.
     * voiceId는 캐릭터마다 다르므로 설정이 아니라 호출부가 넘긴다.
     *
     * <p>⚠ 돌아오는 wav는 <b>44.1kHz 16-bit 모노 고정</b>이다(요청으로 못 바꾼다).
     * 화자를 바꿔도 이 스펙은 변하지 않으므로 CLOVA 시절의 "24000Hz 지원 화자만" 제약은 사라졌다.
     */
    public byte[] synthesize(String text, String voiceId) {
        SynthesisRequest request = new SynthesisRequest(
                voiceId,
                text,
                properties.model(),
                properties.language(),
                new Output(properties.audioFormat()));

        try {
            byte[] audio = restClient.post()
                    .uri("/v1/text-to-speech")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(byte[].class);

            if (audio == null || audio.length == 0) {
                log.error("[Voice] 합성 응답이 비어 있음. voiceId={}, textLength={}", voiceId, text.length());
                throw new BaseException(GeneralErrorCode.EXTERNAL_API_ERROR, "TTS 합성 결과가 비어 있습니다.");
            }
            log.debug("[Voice] 합성 완료. voiceId={}, textLength={}, bytes={}", voiceId, text.length(), audio.length);
            return audio;
        } catch (RestClientException e) {
            // 파이프라인 에러는 서버 로깅 전용 — 프론트로 상세 원인을 내려보내지 않는다.
            log.error("[Voice] 합성 실패. voiceId={}, textLength={}", voiceId, text.length(), e);
            throw new BaseException(GeneralErrorCode.EXTERNAL_API_ERROR, "TTS 합성에 실패했습니다.");
        }
    }

    /**
     * 합성 요청 본문. 필드가 snake_case라 {@code @JsonProperty}로 이름을 고정한다 —
     * 전역 Jackson 네이밍 설정이 바뀌어도 이 계약은 흔들리지 않아야 한다.
     *
     * <p>{@code prompt}(감정)·{@code seed}·{@code output.volume} 등은 <b>일부러 안 보낸다</b>.
     * 통화는 문장마다 따로 합성하므로 감정 프리셋을 걸면 문장 간 톤이 튀고, 기본값(normal)이
     * 대화체에 가장 무난하다. 필요해지면 그때 얹는다.
     */
    private record SynthesisRequest(
            @JsonProperty("voice_id") String voiceId,
            @JsonProperty("text") String text,
            @JsonProperty("model") String model,
            @JsonProperty("language") String language,
            @JsonProperty("output") Output output
    ) {
    }

    private record Output(@JsonProperty("audio_format") String audioFormat) {
    }
}
