package com.example.umcCall.domain.chat.controller;

import com.example.umcCall.domain.chat.dto.response.ChatRoomListResponse;
import com.example.umcCall.domain.chat.service.ChatRoomService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chat-rooms")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    /** 채팅방 목록 조회. is_main 최상단이 나머지는 최신순. */
    @GetMapping
    public ApiResponse<ChatRoomListResponse> getChatRooms(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(ChatRoomListResponse.of(chatRoomService.getChatRooms(memberId)));
    }
}
