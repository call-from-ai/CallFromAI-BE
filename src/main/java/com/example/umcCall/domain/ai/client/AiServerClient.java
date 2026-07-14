package com.example.umcCall.domain.ai.client;

import com.example.umcCall.domain.ai.dto.AiChatRequest;
import com.example.umcCall.domain.ai.dto.AiChatResponse;
import com.example.umcCall.domain.ai.dto.AiHealthResponse;
import com.example.umcCall.domain.ai.exception.AiErrorCode;
import com.example.umcCall.domain.ai.exception.AiServerException;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
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
}
