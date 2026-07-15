package com.example.umcCall.domain.call.client;

import com.example.umcCall.global.apiPayload.code.GeneralErrorCode;
import com.example.umcCall.global.config.ClovaVoiceProperties;
import com.example.umcCall.global.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * CLOVA Voice(TTS) 배관. 텍스트 한 덩이를 보내면 합성된 wav 바이트가 한 번에 돌아온다.
 * (스트리밍 아님 — 지연 완화는 호출부에서 문장 단위로 쪼개 여러 번 부르는 방식)
 * 반환한 wav는 헤더까지 그대로다. 서버는 변환하지 않고 프론트가 헤더로 스펙을 읽는다.
 */
@Slf4j
@Component
public class ClovaVoiceClient {

    private final RestClient restClient;
    private final ClovaVoiceProperties properties;

    public ClovaVoiceClient(RestClient.Builder builder, ClovaVoiceProperties properties) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("X-NCP-APIGW-API-KEY-ID", properties.clientId())
                .defaultHeader("X-NCP-APIGW-API-KEY", properties.clientSecret())
                .build();
    }

    /**
     * 텍스트를 {@code speaker} 목소리로 합성해 wav 바이트를 돌려준다. 실패 시 {@link BaseException}.
     * speaker는 캐릭터마다 다르므로 설정이 아니라 호출부가 넘긴다.
     * ⚠ 24000Hz를 지원하는 화자여야 한다(mijin은 16000 전용 — 다운스트림 계약이 깨짐).
     */
    public byte[] synthesize(String text, String speaker) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("speaker", speaker);
        form.add("format", properties.format());
        form.add("sampling-rate", String.valueOf(properties.samplingRate()));
        form.add("text", text);

        try {
            byte[] audio = restClient.post()
                    .uri("/tts-premium/v1/tts")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(byte[].class);

            if (audio == null || audio.length == 0) {
                log.error("[Voice] 합성 응답이 비어 있음. speaker={}, textLength={}", speaker, text.length());
                throw new BaseException(GeneralErrorCode.EXTERNAL_API_ERROR, "TTS 합성 결과가 비어 있습니다.");
            }
            log.debug("[Voice] 합성 완료. speaker={}, textLength={}, bytes={}", speaker, text.length(), audio.length);
            return audio;
        } catch (RestClientException e) {
            // 파이프라인 에러는 서버 로깅 전용 — 프론트로 상세 원인을 내려보내지 않는다.
            log.error("[Voice] 합성 실패. speaker={}, textLength={}", speaker, text.length(), e);
            throw new BaseException(GeneralErrorCode.EXTERNAL_API_ERROR, "TTS 합성에 실패했습니다.");
        }
    }
}
