package com.example.umcCall.domain.chat.service;

import com.example.umcCall.domain.ai.client.AiServerProperties;
import com.example.umcCall.domain.ai.dto.AiChatRequest;
import com.example.umcCall.domain.ai.dto.AiChatResponse;
import java.time.Duration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 이미지가 포함된 채팅을 AI 서버(/chat)에 <b>multipart/form-data</b>로 보내는 채팅 도메인 전용 클라이언트.
 * <p>텍스트 전용 호출은 AI 도메인의 AiConversationService(JSON)를 그대로 쓰고, 이미지가 있을 때만 이 클라이언트를 쓴다.
 * AI 도메인 파일은 수정하지 않고, 접속 정보(AiServerProperties)와 DTO(AiChatRequest/Response)만 재사용한다.
 *
 * <p>AI 서버 계약(코드 조사 확정): 파트 이름 request(application/json 필수) + image(파일, image/jpeg|png).
 * 이미지 경로는 Gemini를 최대 3회 호출하고 서버 측 타임아웃이 없어, 우리 read timeout이 유일한 방어선이라 넉넉히 잡는다.
 */
@Component
public class AiImageChatClient {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Api-Key";
    private static final String REQUEST_PART = "request";
    private static final String IMAGE_PART = "image";
    /** 이미지 경로는 느리다(Gemini 다회 호출 + 서버 타임아웃 없음). 넉넉히 120초. */
    private static final Duration IMAGE_READ_TIMEOUT = Duration.ofSeconds(120);

    private final RestClient restClient;

    public AiImageChatClient(RestClient.Builder builder, AiServerProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        requestFactory.setReadTimeout(IMAGE_READ_TIMEOUT);

        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(INTERNAL_TOKEN_HEADER, properties.internalToken())
                .build();
    }

    /**
     * 이미지와 채팅 요청을 함께 AI 서버로 보낸다.
     * 실패(4xx/5xx/타임아웃/연결오류)는 RuntimeException으로 던지고, 호출부(디바운서)가 잡아 로깅한다.
     *
     * @param request     AI 요청(이미지-only면 message가 빈 문자열일 수 있다 — 서버가 허용)
     * @param imageBytes  이미지 원본 바이트(≤10MB)
     * @param contentType 이미지 MIME(image/jpeg|png)
     */
    public AiChatResponse chat(AiChatRequest request, byte[] imageBytes, String contentType) {
        MediaType imageType = resolveImageType(contentType);

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part(REQUEST_PART, request, MediaType.APPLICATION_JSON);   // JSON 파트 Content-Type 필수
        bodyBuilder.part(IMAGE_PART, new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return "image" + extensionFor(imageType);   // 파트에 filename이 있어야 서버가 파일로 인식
            }
        }, imageType);
        MultiValueMap<String, org.springframework.http.HttpEntity<?>> body = bodyBuilder.build();

        try {
            AiChatResponse response = restClient.post()
                    .uri("/chat")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        HttpStatusCode status = res.getStatusCode();
                        // 4xx: 잘못된 요청 → 다시 보내도 안 됨 → 재시도 불가 예외(디바운서가 스킵)
                        // 5xx: 서버 일시 오류 → 재시도 대상(일반 RuntimeException)
                        if (status.is4xxClientError()) {
                            throw new AiImageRequestRejectedException("AI 이미지 요청 거부(4xx). status=" + status);
                        }
                        throw new RuntimeException("AI 이미지 요청 실패. status=" + status);
                    })
                    .body(AiChatResponse.class);
            if (response == null) {
                throw new RuntimeException("AI 이미지 요청이 빈 응답을 반환했습니다.");
            }
            return response;
        } catch (RestClientException e) {
            throw new RuntimeException("AI 이미지 요청 통신 오류", e);
        }
    }

    /** S3 저장 content-type을 MediaType으로. 알 수 없으면 jpeg로 본다(업로드 시 jpeg/png만 허용됨). */
    private MediaType resolveImageType(String contentType) {
        if (MediaType.IMAGE_PNG_VALUE.equals(contentType)) {
            return MediaType.IMAGE_PNG;
        }
        return MediaType.IMAGE_JPEG;
    }

    private String extensionFor(MediaType imageType) {
        return MediaType.IMAGE_PNG.equals(imageType) ? ".png" : ".jpg";
    }
}
