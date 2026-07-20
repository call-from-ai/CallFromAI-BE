package com.example.umcCall.domain.chat.controller;

import com.example.umcCall.domain.chat.service.ChatSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 채팅 실시간 이벤트 구독(SSE). 유저당 전역 연결 1개로 자기 모든 방의 이벤트를 받는다.
 * 경로 "/subscribe"는 리터럴이라 "/{chatRoomId}"보다 우선 매칭
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat-rooms")
public class ChatSseController {

    private final ChatSseService chatSseService;

    /** SSE 구독. text/event-stream으로 연결을 열어 유지한다. */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal Long memberId) {
        return chatSseService.subscribe(memberId);
    }
}
