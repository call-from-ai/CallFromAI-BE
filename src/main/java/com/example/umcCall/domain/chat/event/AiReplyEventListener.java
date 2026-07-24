package com.example.umcCall.domain.chat.event;

import com.example.umcCall.domain.chat.service.AiReplyDebouncer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 유저 메시지 전송 커밋 후, AI 답장 디바운서에 "방에 활동이 있었다"는 신호만 전달한다.
 * 실제 답장 생성(모으기·AI 호출·저장·SSE)은 디바운서가 방 단위로 처리한다.
 * 커밋 후(AFTER_COMMIT)라 방금 저장된 유저 메시지가 배치 조회에 반드시 보인다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiReplyEventListener {

    private final AiReplyDebouncer aiReplyDebouncer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserMessageSent(UserMessageSentEvent event) {
        try {
            aiReplyDebouncer.onActivity(event.chatRoomId());
        } catch (Exception e) {
            // 스케줄 등록 실패가 유저 전송(이미 201 반환)에 영향을 주지 않도록 로깅만 한다.
            log.error("AI 답장 스케줄 등록 실패. chatRoomId={}", event.chatRoomId(), e);
        }
    }
}
