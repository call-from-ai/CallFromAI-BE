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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.function.Consumer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
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

    /** SSE 이벤트 이름(AI 서버 {@code ChatService.sendMessageStream} 기준). meta/done은 통화가 쓰지 않는다. */
    private static final String CHUNK_EVENT = "chunk";
    private static final String ERROR_EVENT = "error";

    private final RestClient restClient;
    /**
     * 스트리밍({@code /chat/stream}) 전용. 단발과 <b>read timeout만</b> 다르다.
     * <p>⚠ 분리한 이유: 스트리밍의 read timeout은 <b>조각 사이 침묵</b>의 상한인데, 단발용 값(운영 60초)을
     * 그대로 쓰면 half-open 연결 하나가 그 통화의 워커를 60초간 붙잡는다(통화가 그동안 벙어리가 된다).
     * <p>커넥션 풀은 공유하지 않는다 — {@link SimpleClientHttpRequestFactory}(JDK {@code HttpURLConnection})라
     * 풀 자체가 없다. 풀 기반 팩토리로 바꾸면 <b>스트림이 채팅 요청을 굶기지 않도록</b> 풀도 갈라야 한다.
     */
    private final RestClient streamRestClient;
    private final ObjectMapper objectMapper;

    public AiServerClient(RestClient.Builder builder, AiServerProperties properties,
                          ObjectMapper objectMapper) {
        this.restClient = buildClient(builder, properties, properties.readTimeoutMs());
        this.streamRestClient = buildClient(builder, properties, properties.streamReadTimeoutMs());
        this.objectMapper = objectMapper;
    }

    private static RestClient buildClient(RestClient.Builder builder, AiServerProperties properties,
                                          int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return builder.clone()
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

    /**
     * {@code POST /chat/stream}(SSE). AI 대사를 <b>만들어지는 대로</b> 받아 조각마다 {@code onChunk}를 부른다.
     * 요청 body는 {@link #chat}과 완전히 같다 — 계약 DTO를 그대로 재사용한다.
     *
     * <p>왜 필요한가: {@link #chat}은 LLM이 대사를 <b>다 만들 때까지</b> 한 글자도 주지 않아(실측 3~5초)
     * 그동안 TTS를 시작할 수 없다. 스트리밍은 첫 문장이 나오는 즉시 합성·송신할 수 있어 통화 TTFA를 줄인다.
     *
     * <p>⚠ <b>{@code onChunk}는 호출 스레드에서 동기로 불린다</b> — 통화 워커가 이 호출을 잡고 있는 동안
     * 조각이 도착할 때마다 실행된다. 여기서 오래 걸리는 일(TTS 합성·송신)을 하면 그만큼 다음 조각 수신이
     * 늦어지지만, 통화는 어차피 순차 재생이라 그게 맞는 동작이다.
     *
     * <p>⚠ 실패 두 가지를 <b>둘 다</b> 막아야 한다: ⓐ HTTP 상태 오류(요청 자체가 거절), ⓑ 스트림 중간에 오는
     * {@code event: error}. ⓑ는 <b>HTTP 200 안에서</b> 오므로 상태코드로는 절대 잡히지 않는다.
     */
    public void chatStream(AiChatRequest request, Consumer<String> onChunk) {
        try {
            streamRestClient.post()
                    .uri("/chat/stream")
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .body(request)
                    .exchange((httpRequest, httpResponse) -> {
                        if (httpResponse.getStatusCode().isError()) {
                            String responseBody = StreamUtils.copyToString(
                                    httpResponse.getBody(), StandardCharsets.UTF_8);
                            log.error("AI 채팅 스트림 API 오류. status={}, body={}",
                                    httpResponse.getStatusCode(), responseBody);
                            throw new AiServerException(AiErrorCode.AI_SERVER_ERROR);
                        }
                        readEventStream(httpResponse.getBody(), onChunk);
                        return null;
                    });
        } catch (AiServerException exception) {
            throw exception;
        } catch (RestClientException | UncheckedIOException exception) {
            throw new AiServerException(AiErrorCode.AI_SERVER_UNAVAILABLE, exception);
        }
    }

    /**
     * SSE 본문을 <b>도착하는 대로</b> 한 줄씩 읽는다. 형식은 {@code event:}/{@code data:} 줄과 빈 줄(이벤트 경계)뿐이다.
     * <p>{@code readLine()}은 다음 줄이 올 때까지 블로킹한다 — 통화 워커(통화당 단일 스레드)가 어차피
     * 응답을 기다리던 자리라 스레드 모델은 그대로다. 응답을 통째로 버퍼링하지 않는 게 이 메서드의 존재 이유다.
     */
    private void readEventStream(InputStream body, Consumer<String> onChunk) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
        String eventName = null;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                eventName = null; // 빈 줄 = 이벤트 끝. 다음 이벤트가 이름 없이 시작하는 걸 막는다.
            } else if (line.startsWith("event:")) {
                eventName = line.substring("event:".length()).trim();
            } else if (line.startsWith("data:")) {
                handleEvent(eventName, line.substring("data:".length()).trim(), onChunk);
            }
            // id:/retry:/주석(:)은 AI 서버가 보내지 않는다. 와도 무시하는 게 SSE 규약이다.
        }
    }

    /** 이벤트 하나를 처리한다. 통화가 쓰는 건 {@code chunk}뿐이고, {@code meta}/{@code done}은 통계·메타라 버린다. */
    private void handleEvent(String eventName, String data, Consumer<String> onChunk) {
        if (ERROR_EVENT.equals(eventName)) {
            // ⚠ 여기까지 HTTP는 200이다. 이 분기가 없으면 실패가 "짧은 응답"으로 조용히 성공 처리된다.
            log.error("AI 채팅 스트림 오류 이벤트. body={}", data);
            throw new AiServerException(AiErrorCode.AI_SERVER_ERROR);
        }
        if (!CHUNK_EVENT.equals(eventName)) {
            return;
        }
        try {
            JsonNode text = objectMapper.readTree(data).path("text");
            if (text.isTextual() && !text.asText().isEmpty()) {
                onChunk.accept(text.asText());
            }
        } catch (JsonProcessingException exception) {
            // 조각 하나가 깨졌다고 이미 말하기 시작한 턴을 통째로 버리진 않는다 — 그 조각만 건너뛴다.
            log.warn("AI 채팅 스트림 chunk 파싱 실패(건너뜀). data={}", data, exception);
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
