package com.example.umcCall.domain.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.umcCall.domain.ai.exception.AiErrorCode;
import com.example.umcCall.domain.ai.exception.AiServerException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class AiServerClientStreamTest {

    private AiServerClient client;

    @BeforeEach
    void setUp() {
        AiServerProperties properties = new AiServerProperties(
                "http://localhost", 1_000, 1_000, 1_000, "test-token");
        client = new AiServerClient(RestClient.builder(), properties, new ObjectMapper());
    }

    @Test
    void 유효한_chunk와_done을_수신하면_정상_종료한다() throws Exception {
        List<String> chunks = new ArrayList<>();

        client.readEventStream(stream("""
                event: chunk
                data: {"text":"안녕"}

                event: chunk
                data: {"text":"하세요"}

                event: done
                data: {}

                """), chunks::add);

        assertThat(chunks).containsExactly("안녕", "하세요");
    }

    @Test
    void 이벤트_없이_EOF가_오면_비정상_종료로_처리한다() {
        assertErrorCode("", AiErrorCode.AI_SERVER_ERROR);
    }

    @Test
    void chunk_뒤_done_없이_EOF가_오면_비정상_종료로_처리한다() {
        assertErrorCode("""
                event: chunk
                data: {"text":"일부 응답"}

                """, AiErrorCode.AI_SERVER_ERROR);
    }

    @Test
    void done만_수신하면_빈_응답으로_처리한다() {
        assertErrorCode("""
                event: done
                data: {}

                """, AiErrorCode.EMPTY_AI_RESPONSE);
    }

    @Test
    void 빈_chunk는_유효한_chunk로_세지_않는다() {
        assertErrorCode("""
                event: chunk
                data: {"text":""}

                event: done
                data: {}

                """, AiErrorCode.EMPTY_AI_RESPONSE);
    }

    private void assertErrorCode(String sse, AiErrorCode expected) {
        assertThatThrownBy(() -> client.readEventStream(stream(sse), ignored -> { }))
                .isInstanceOf(AiServerException.class)
                .extracting(exception -> ((AiServerException) exception).getErrorCode())
                .isEqualTo(expected);
    }

    private ByteArrayInputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
