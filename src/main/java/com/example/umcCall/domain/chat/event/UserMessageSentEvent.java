package com.example.umcCall.domain.chat.event;

/**
 * 유저 메시지가 저장된 직후 발행되는 이벤트.
 * 트랜잭션 커밋 후 비동기로 AI 답장 생성을 트리거
 *
 * @param chatRoomId     대상 채팅방
 * @param memberId       SSE로 답장을 보낼 유저
 * @param userMessageId  방금 저장된 유저 메시지 id(대화 이력에서 제외용)
 * @param userMessage    유저가 보낸 내용(AI에 전달)
 */
public record UserMessageSentEvent(
        Long chatRoomId,
        Long memberId,
        Long userMessageId,
        String userMessage
) {
}
