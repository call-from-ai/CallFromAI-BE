package com.example.umcCall.domain.chat.controller;

import com.example.umcCall.domain.chat.dto.response.ChatMessageCursorResponse;
import com.example.umcCall.domain.chat.service.ChatMessageService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chat-rooms/{chatRoomId}/messages")
public class ChatMessageController {

    // TODO: 준혁님 인증 연동 시 @AuthenticationPrincipal로 현재 회원 id를 받도록 교체
    private static final Long TEMP_MEMBER_ID = 1L;

    private final ChatMessageService chatMessageService;

    /** 채팅 메시지 커서 조회. cursor 이전(더 과거) size개, 과거→최신 순으로 반환. */
    @GetMapping
    public ApiResponse<ChatMessageCursorResponse> getMessages(
            @PathVariable Long chatRoomId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(
                chatMessageService.getMessages(TEMP_MEMBER_ID, chatRoomId, cursor, size));
    }
}
