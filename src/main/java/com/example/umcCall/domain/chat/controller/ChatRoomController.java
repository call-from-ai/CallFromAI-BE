package com.example.umcCall.domain.chat.controller;

import com.example.umcCall.domain.chat.dto.response.ChatRoomListResponse;
import com.example.umcCall.domain.chat.service.ChatRoomService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chat-rooms")
public class ChatRoomController {

    // TODO: 준혁님 인증 연동 시 @AuthenticationPrincipal로 현재 회원 id를 받도록 교체
    private static final Long TEMP_MEMBER_ID = 1L;

    private final ChatRoomService chatRoomService;

    /** 채팅방 목록 조회. is_main 최상단 → 나머지 최신순. */
    @GetMapping
    public ApiResponse<ChatRoomListResponse> getChatRooms() {
        return ApiResponse.onSuccess(ChatRoomListResponse.of(chatRoomService.getChatRooms(TEMP_MEMBER_ID)));
    }
}
