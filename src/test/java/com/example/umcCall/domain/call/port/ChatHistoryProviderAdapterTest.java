package com.example.umcCall.domain.call.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.umcCall.domain.ai.dto.AiChatHistoryItem;
import com.example.umcCall.domain.chat.entity.ChatMessage;
import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.enums.SenderType;
import com.example.umcCall.domain.chat.repository.ChatMessageRepository;
import com.example.umcCall.domain.chat.repository.ChatRoomRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Pageable;
import org.mockito.junit.jupiter.MockitoExtension;

/** 채팅 최근 대화를 AI 히스토리로 변환하는 어댑터 검증(방 없음/정렬·role 매핑). */
@ExtendWith(MockitoExtension.class)
class ChatHistoryProviderAdapterTest {

    private static final Long RELATIONSHIP_ID = 20L;
    private static final Long ROOM_ID = 100L;

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatMessageRepository chatMessageRepository;

    @InjectMocks private ChatHistoryProviderAdapter adapter;

    @Test
    void 채팅방이_없으면_빈_히스토리를_준다() {
        when(chatRoomRepository.findByRelationshipId(RELATIONSHIP_ID)).thenReturn(Optional.empty());

        assertThat(adapter.recentHistory(RELATIONSHIP_ID, 20)).isEmpty();
    }

    @Test
    void 최신순으로_뽑힌_메시지를_과거순으로_뒤집고_role을_매핑한다() {
        ChatRoom room = mock(ChatRoom.class);
        when(chatRoomRepository.findByRelationshipId(RELATIONSHIP_ID)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(ROOM_ID);

        // 쿼리는 최신→과거(DESC)로 준다: AI "반가워"(나중) → USER "안녕"(먼저).
        ChatMessage aiMsg = message(SenderType.AI, "반가워", LocalDateTime.now());
        ChatMessage userMsg = message(SenderType.USER, "안녕", LocalDateTime.now().minusMinutes(1));
        when(chatMessageRepository.findMessagesByCursor(eq(ROOM_ID), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new ArrayList<>(List.of(aiMsg, userMsg)));

        List<AiChatHistoryItem> history = adapter.recentHistory(RELATIONSHIP_ID, 20);

        // 과거→최신으로 뒤집혀야 하고, USER→"user" / AI→"assistant"로 매핑돼야 한다.
        assertThat(history).extracting(AiChatHistoryItem::sender).containsExactly("user", "assistant");
        assertThat(history).extracting(AiChatHistoryItem::content).containsExactly("안녕", "반가워");
    }

    private ChatMessage message(SenderType sender, String content, LocalDateTime createdAt) {
        ChatMessage m = mock(ChatMessage.class);
        when(m.getSenderType()).thenReturn(sender);
        when(m.getContent()).thenReturn(content);
        when(m.getCreatedAt()).thenReturn(createdAt);
        return m;
    }
}
