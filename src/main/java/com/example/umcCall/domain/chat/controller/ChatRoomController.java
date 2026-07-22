package com.example.umcCall.domain.chat.controller;

import com.example.umcCall.domain.chat.dto.response.CharacterRoomHeader;
import com.example.umcCall.domain.chat.dto.response.ChatRoomListResponse;
import com.example.umcCall.domain.chat.service.ChatRoomService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    /** 채팅방 상세(헤더) 조회. 캐릭터 이름/사진/dDay/전화버튼(isMain) 정보를 반환 */
    @GetMapping("/{chatRoomId}")
    public ApiResponse<CharacterRoomHeader> getRoomHeader(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long chatRoomId) {
        return ApiResponse.onSuccess(chatRoomService.getRoomHeader(memberId, chatRoomId));

    }
}
