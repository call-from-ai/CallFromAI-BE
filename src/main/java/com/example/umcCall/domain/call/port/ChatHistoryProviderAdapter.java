package com.example.umcCall.domain.call.port;

import com.example.umcCall.domain.ai.dto.AiChatHistoryItem;
import com.example.umcCall.domain.chat.entity.ChatMessage;
import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.enums.SenderType;
import com.example.umcCall.domain.chat.repository.ChatMessageRepository;
import com.example.umcCall.domain.chat.repository.ChatRoomRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ChatHistoryProvider} 어댑터. 채팅 저장소(방·메시지)를 직접 읽어 AI 히스토리로 변환한다.
 * 이 클래스만 채팅 내부 구조를 알고, 통화의 나머지 코드는 포트 인터페이스만 본다.
 * <p>변환 규칙은 채팅 자신의 AI 맥락 조립({@code AiReplyDebouncer.buildHistory})과 동일하게 맞춘다 —
 * 통화는 "채팅 AI가 보던 것과 같은 맥락"을 이어받아야 하므로 가시성 컷(messageVisibleAfterId)을 적용하지 않는다.
 */
@Component
@RequiredArgsConstructor
class ChatHistoryProviderAdapter implements ChatHistoryProvider {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AiChatHistoryItem> recentHistory(Long relationshipId, int limit) {
        return chatRoomRepository.findByRelationshipId(relationshipId)
                .map(room -> toHistory(room, limit))
                .orElseGet(List::of);
    }

    private List<AiChatHistoryItem> toHistory(ChatRoom room, int limit) {
        // cursor=null → 최신부터, cutoff=null → 가시성 컷 무시(채팅 AI 맥락과 동일). 삭제 메시지는 쿼리가 제외.
        List<ChatMessage> recent = new ArrayList<>(chatMessageRepository.findMessagesByCursor(
                room.getId(), null, null, PageRequest.of(0, limit)));
        Collections.reverse(recent); // 최신→과거로 뽑히니 과거→최신으로 뒤집는다
        return recent.stream()
                .map(m -> new AiChatHistoryItem(toRole(m.getSenderType()), m.getContent(), m.getCreatedAt()))
                .toList();
    }

    /** 채팅 발신자 타입을 AI 계약값으로. 유저는 "user", 나머지(AI/SYSTEM)는 "assistant". */
    private String toRole(SenderType senderType) {
        return senderType == SenderType.USER ? "user" : "assistant";
    }
}
