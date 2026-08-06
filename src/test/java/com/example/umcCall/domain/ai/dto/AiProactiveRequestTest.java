package com.example.umcCall.domain.ai.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.umcCall.domain.ai.enums.AiConversationChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiProactiveRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

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
                null,
                null,
                List.of());

        String json = objectMapper.writeValueAsString(request);

        assertThat(json).contains("\"channel\":\"CHAT\"");
    }
}
