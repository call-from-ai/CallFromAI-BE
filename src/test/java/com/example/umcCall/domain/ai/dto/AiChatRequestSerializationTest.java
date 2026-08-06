package com.example.umcCall.domain.ai.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.umcCall.domain.ai.enums.AiConversationChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class AiChatRequestSerializationTest {

    @Test
    void 서울_현지시각의_오프셋을_유지해_문자열로_직렬화한다() throws Exception {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .timeZone("Asia/Seoul")
                .build();
        AiChatRequest request = new AiChatRequest(
                "request-1",
                1L,
                AiConversationChannel.CHAT,
                "민준",
                "Asia/Seoul",
                OffsetDateTime.parse("2026-08-06T21:29:18.716127+09:00"),
                "지금 몇 시야?",
                null,
                null,
                List.of());

        String json = objectMapper.writeValueAsString(request);

        assertThat(json).contains("\"userName\":\"민준\"");
        assertThat(json).contains("\"userTimeZone\":\"Asia/Seoul\"");
        assertThat(json).contains(
                "\"localDateTime\":\"2026-08-06T21:29:18.716127+09:00\"");
    }
}
