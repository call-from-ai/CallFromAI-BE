package com.example.umcCall.domain.chat.controller;

import com.example.umcCall.domain.chat.dto.response.ChatMessageCursorResponse;
import com.example.umcCall.domain.chat.dto.response.ChatMessageResponse;
import com.example.umcCall.domain.chat.service.ChatMessageService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import com.example.umcCall.global.apiPayload.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "채팅 메시지", description = "채팅 메시지 조회/전송 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat-rooms/{chatRoomId}/messages")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    /** 채팅 메시지 커서 조회. cursor 이전꺼 size개, 과거에서 최신 순으로 반환. */
    @Operation(summary = "채팅 메시지 조회",
            description = "커서 기반으로 과거 메시지를 조회한다. cursor 이전 size개를 과거 -> 최신 순으로 반환한다."
                    + " size 기본 30, 최대 50.")
    @GetMapping
    public ApiResponse<ChatMessageCursorResponse> getMessages(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long chatRoomId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(
                chatMessageService.getMessages(memberId, chatRoomId, cursor, size));
    }

    /** 채팅 메시지 전송(텍스트/사진). 저장된 유저 메시지만 반환(AI 답장은 이후 SSE). */
    @Operation(summary = "채팅 메시지 전송",
            description = "텍스트/사진 메시지를 전송하고 저장된 메시지를 반환한다. content·image 중 최소 하나가 필요하며,"
                    + " 이미지는 JPEG/PNG만 허용한다. AI 답장은 이후 SSE로 별도 전달된다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChatMessageResponse> sendMessage(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long chatRoomId,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) MultipartFile image) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.CREATED,
                chatMessageService.sendMessage(memberId, chatRoomId, content, image));
    }

    /** 채팅 메시지 삭제(물리). 사진 메시지면 S3 객체까지 삭제된다. */
    @Operation(summary = "채팅 메시지 삭제",
            description = "메시지를 삭제한다. 유저/AI 메시지 모두 가능하며, 사진(또는 텍스트+사진) 메시지는 통째로 삭제된다.")
    @DeleteMapping("/{messageId}")
    public ApiResponse<Void> deleteMessage(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long chatRoomId,
            @PathVariable Long messageId) {
        chatMessageService.deleteMessage(memberId, chatRoomId, messageId);
        return ApiResponse.onSuccess();
    }
}
