package com.example.umcCall.domain.ai.client;

import com.example.umcCall.domain.ai.dto.AiChatRequest;
import com.example.umcCall.domain.ai.dto.AiChatResponse;
import com.example.umcCall.domain.ai.dto.AiHealthResponse;
import com.example.umcCall.domain.ai.dto.AiCharacterSnapshot;
import com.example.umcCall.domain.ai.dto.AiProactiveRequest;
import com.example.umcCall.domain.ai.dto.AiSummaryRequest;
import com.example.umcCall.domain.ai.dto.AiSummaryResponse;
import com.example.umcCall.domain.ai.exception.AiErrorCode;
import com.example.umcCall.domain.ai.exception.AiServerException;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.nio.charset.StandardCharsets;
import org.springframework.util.StreamUtils;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@EnableConfigurationProperties(AiServerProperties.class)
public class AiServerClient {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Api-Key";

    private final RestClient restClient;

    public AiServerClient(RestClient.Builder builder, AiServerProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));

        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(INTERNAL_TOKEN_HEADER, properties.internalToken())
                .build();
    }

    public AiChatResponse chat(AiChatRequest request) {
        try {
            AiChatResponse response = restClient.post()
                    .uri("/chat")
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (httpRequest, httpResponse) -> {
                        String responseBody = StreamUtils.copyToString(
                                httpResponse.getBody(), StandardCharsets.UTF_8);
                        log.error("AI 채팅 API 오류. status={}, body={}",
                                httpResponse.getStatusCode(), responseBody);
                        throw new AiServerException(AiErrorCode.AI_SERVER_ERROR);
                    })
                    .body(AiChatResponse.class);
            if (response == null) {
                throw new AiServerException(AiErrorCode.EMPTY_AI_RESPONSE);
            }
            return response;
        } catch (AiServerException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new AiServerException(AiErrorCode.AI_SERVER_UNAVAILABLE, exception);
        }
    }

    public AiChatResponse proactive(AiProactiveRequest request) {
        try {
            AiChatResponse response = restClient.post()
                    .uri("/api/chat/proactive/send")
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.value() == HttpStatus.CONFLICT.value(),
                            (httpRequest, httpResponse) -> {
                                throw new AiServerException(AiErrorCode.DUPLICATE_REQUEST);
                            })
                    .onStatus(HttpStatusCode::isError, (httpRequest, httpResponse) -> {
                        String responseBody = StreamUtils.copyToString(
                                httpResponse.getBody(), StandardCharsets.UTF_8);
                        log.error("AI 선제 연락 API 오류. status={}, body={}",
                                httpResponse.getStatusCode(), responseBody);
                        throw new AiServerException(AiErrorCode.AI_SERVER_ERROR);
                    })
                    .body(AiChatResponse.class);
            if (response == null) {
                throw new AiServerException(AiErrorCode.EMPTY_AI_RESPONSE);
            }
            return response;
        } catch (AiServerException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new AiServerException(AiErrorCode.AI_SERVER_UNAVAILABLE, exception);
        }
    }

    public AiHealthResponse health() {
        try {
            AiHealthResponse response = restClient.get()
                    .uri("/actuator/health")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (httpRequest, httpResponse) -> {
                        throw new AiServerException(AiErrorCode.AI_SERVER_ERROR);
                    })
                    .body(AiHealthResponse.class);
            if (response == null) {
                throw new AiServerException(AiErrorCode.EMPTY_AI_RESPONSE);
            }
            return response;
        } catch (AiServerException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new AiServerException(AiErrorCode.AI_SERVER_UNAVAILABLE, exception);
        }
    }

    public AiSummaryResponse summarize(AiSummaryRequest request) {
        try {
            AiSummaryResponse response = restClient.post()
                    .uri("/internal/conversations/summary")
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (httpRequest, httpResponse) -> {
                        String responseBody = StreamUtils.copyToString(
                                httpResponse.getBody(), StandardCharsets.UTF_8);
                        log.error("AI 대화 요약 API 오류. status={}, body={}",
                                httpResponse.getStatusCode(), responseBody);
                        throw new AiServerException(AiErrorCode.AI_SERVER_ERROR);
                    })
                    .body(AiSummaryResponse.class);
            if (response == null || response.summary() == null || response.summary().isBlank()) {
                throw new AiServerException(AiErrorCode.EMPTY_AI_RESPONSE);
            }
            return response;
        } catch (AiServerException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new AiServerException(AiErrorCode.AI_SERVER_UNAVAILABLE, exception);
        }
    }

    public void syncCharacter(AiCharacterSnapshot snapshot) {
        try {
            restClient.put()
                    .uri("/internal/characters/{characterId}/snapshot", snapshot.characterId())
                    .body(snapshot)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (httpRequest, httpResponse) -> {
                        String responseBody = StreamUtils.copyToString(
                                httpResponse.getBody(), StandardCharsets.UTF_8);
                        log.error("AI snapshot API 오류. status={}, body={}",
                                httpResponse.getStatusCode(), responseBody);
                        throw new AiServerException(AiErrorCode.AI_SERVER_ERROR);
                    })
                    .toBodilessEntity();
        } catch (AiServerException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new AiServerException(AiErrorCode.AI_SERVER_UNAVAILABLE, exception);
        }
    }

    public void deleteCharacterData(Long characterId) {
        try {
            restClient.delete()
                    .uri("/internal/characters/{characterId}/data", characterId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (httpRequest, httpResponse) -> {
                        throw new AiServerException(AiErrorCode.AI_SERVER_ERROR);
                    })
                    .toBodilessEntity();
        } catch (AiServerException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new AiServerException(AiErrorCode.AI_SERVER_UNAVAILABLE, exception);
        }
    }
}
