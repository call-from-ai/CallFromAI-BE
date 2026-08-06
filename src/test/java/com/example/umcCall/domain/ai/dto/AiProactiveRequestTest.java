package com.example.umcCall.domain.ai.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.umcCall.domain.ai.enums.AiConversationChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiProactiveRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void proactiveRequestContainsChatChannelRequiredByAiServer() throws Exception {
        AiProactiveRequest request = new AiProactiveRequest(
                "proactive-test-request",
                1L,
                AiConversationChannel.CHAT,
                "Send a proactive message.",
                "NORMAL_CHECK_IN",
                "NORMAL",
                "POSITIVE",
                "민준",
                "Asia/Seoul",
                OffsetDateTime.parse("2026-08-07T02:15:00+09:00"),
                null,
                null,
                List.of());

        String json = objectMapper.writeValueAsString(request);

        assertThat(json).contains("\"channel\":\"CHAT\"");
        assertThat(json).contains("\"userName\":\"민준\"");
        assertThat(json).contains("\"userTimeZone\":\"Asia/Seoul\"");
        assertThat(json).contains("\"localDateTime\":\"2026-08-07T02:15:00+09:00\"");
    }
}
