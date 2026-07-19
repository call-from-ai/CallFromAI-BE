package com.example.umcCall.domain.chat.controller;

import com.example.umcCall.domain.chat.dto.response.ChatMessageCursorResponse;
import com.example.umcCall.domain.chat.dto.response.ChatMessageResponse;
import com.example.umcCall.domain.chat.service.ChatMessageService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import com.example.umcCall.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chat-rooms/{chatRoomId}/messages")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    /** 채팅 메시지 커서 조회. cursor 이전꺼 size개, 과거에서 최신 순으로 반환. */
    @GetMapping
    public ApiResponse<ChatMessageCursorResponse> getMessages(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long chatRoomId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(
                chatMessageService.getMessages(memberId, chatRoomId, cursor, size));
    }

    /** 채팅 메시지 전송(텍스트). 저장된 유저 메시지만 반환(AI 답장은 이후 SSE). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChatMessageResponse> sendMessage(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long chatRoomId,
            @RequestParam(required = false) String content) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.CREATED,
                chatMessageService.sendMessage(memberId, chatRoomId, content));
    }
}
