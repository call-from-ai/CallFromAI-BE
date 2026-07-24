package com.example.umcCall.domain.chat.event;

/**
 * 유저 메시지가 저장된 직후 발행되는 이벤트.
 * 커밋 후 AI 답장 디바운서에 "이 방에 활동이 있었다"는 신호로만 쓰인다.
 * 실제 처리할 메시지들은 디바운서가 방 단위 배치로 다시 조회하므로, 개별 메시지 내용/id는 싣지 않는다.
 *
 * @param chatRoomId 활동이 발생한 채팅방
 */
public record UserMessageSentEvent(
        Long chatRoomId
) {
}
