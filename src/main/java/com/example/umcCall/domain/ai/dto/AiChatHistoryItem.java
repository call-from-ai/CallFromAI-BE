package com.example.umcCall.domain.ai.dto;

import java.time.LocalDateTime;

public record AiChatHistoryItem(
        String sender,
        String content,
        LocalDateTime createdAt
) {
}
