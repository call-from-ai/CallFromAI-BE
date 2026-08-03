package com.example.umcCall.domain.chat.controller;

import com.example.umcCall.domain.chat.dto.request.ChatRoomMuteRequest;
import com.example.umcCall.domain.chat.dto.response.CharacterRoomHeader;
import com.example.umcCall.domain.chat.dto.response.ChatRoomListResponse;
import com.example.umcCall.domain.chat.dto.response.ChatRoomMuteResponse;
import com.example.umcCall.domain.chat.service.ChatRoomService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "채팅방", description = "채팅방 목록/상세 조회 및 읽음, 음소거 설정 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat-rooms")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    /** 채팅방 목록 조회. is_main 최상단이 나머지는 최신순. */
    @Operation(summary = "채팅방 목록 조회",
            description = "내 채팅방 목록을 반환한다. 메인 연인방이 최상단, 나머지는 마지막 메시지 최신순.")
    @GetMapping
    public ApiResponse<ChatRoomListResponse> getChatRooms(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(ChatRoomListResponse.of(chatRoomService.getChatRooms(memberId)));
    }

    /** 채팅방 상세(헤더) 조회. 캐릭터 이름/사진/dDay/전화버튼(isMain) 정보를 반환 */
    @Operation(summary = "채팅방 상세(헤더) 조회",
            description = "방 진입 시 헤더용 캐릭터 정보(이름·이미지·dDay·메인 여부)를 반환한다.")
    @GetMapping("/{chatRoomId}")
    public ApiResponse<CharacterRoomHeader> getRoomHeader(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long chatRoomId) {
        return ApiResponse.onSuccess(chatRoomService.getRoomHeader(memberId, chatRoomId));

    }

    /** 채팅방 읽음 처리. 방의 안 읽은 수신 메시지를 일괄 읽음 처리한다. */
    @Operation(summary = "채팅방 읽음 처리",
            description = "방의 안 읽은 수신 메시지(상대 메시지)를 일괄 읽음 처리한다.")
    @PatchMapping("/{chatRoomId}/read")
    public ApiResponse<Void> markAsRead(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long chatRoomId) {
        chatRoomService.markAsRead(memberId, chatRoomId);
        return ApiResponse.onSuccess();
    }

    /** 채팅방 음소거 설정. 요청받은 상태를 그대로 반영 */
    @Operation(summary = "채팅방 음소거 설정",
            description = "요청한 상태로 음소거를 설정한다(토글 아님). 음소거 시 이후 FCM 푸시를 건너뛴다.")
    @PatchMapping("/{chatRoomId}/mute")
    public ApiResponse<ChatRoomMuteResponse> updateMute(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long chatRoomId,
            @Valid @RequestBody ChatRoomMuteRequest request) {
        return ApiResponse.onSuccess(chatRoomService.updateMute(memberId, chatRoomId, request.isMuted()));
    }

    /** 채팅방 목록에서 숨김. AI가 새 메시지를 보내면 목록에 다시 나타난다. */
    @Operation(summary = "채팅방 숨김",
            description = "채팅방을 목록에서 숨긴다. 지금까지의 메시지는 감춰지고, 이후 AI가 새 메시지를 보내면 목록에 다시 나타난다.")
    @DeleteMapping("/{chatRoomId}")
    public ApiResponse<Void> hideRoom(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long chatRoomId) {
        chatRoomService.hideRoom(memberId, chatRoomId);
        return ApiResponse.onSuccess();
    }
}
