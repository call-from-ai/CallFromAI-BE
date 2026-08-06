package com.example.umcCall.domain.relationship.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.umcCall.domain.ai.client.AiServerClient;
import com.example.umcCall.domain.ai.dto.AiSummaryRequest;
import com.example.umcCall.domain.ai.dto.AiSummaryResponse;
import com.example.umcCall.domain.chat.entity.ChatMessage;
import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.enums.SenderType;
import com.example.umcCall.domain.chat.repository.ChatMessageRepository;
import com.example.umcCall.domain.chat.repository.ChatRoomRepository;
import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.member.entity.Member;
import com.example.umcCall.domain.member.repository.MemberRepository;
import com.example.umcCall.domain.relationship.entity.ChatSummary;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.repository.ChatSummaryRepository;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ChatSummaryServiceTest {

    @Mock RelationshipRepository relationshipRepository;
    @Mock ChatRoomRepository chatRoomRepository;
    @Mock ChatMessageRepository chatMessageRepository;
    @Mock ChatSummaryRepository chatSummaryRepository;
    @Mock MemberRepository memberRepository;
    @Mock AiServerClient aiServerClient;

    private ChatSummaryService service;

    @BeforeEach
    void setUp() {
        service = new ChatSummaryService(
                relationshipRepository,
                chatRoomRepository,
                chatMessageRepository,
                chatSummaryRepository,
                memberRepository,
                aiServerClient);
    }

    @Test
    void returnsCachedSummaryWhenLastMessageHasNotChanged() {
        Relationship relationship = org.mockito.Mockito.mock(Relationship.class);
        ChatRoom room = org.mockito.Mockito.mock(ChatRoom.class);
        ChatMessage lastMessage = org.mockito.Mockito.mock(ChatMessage.class);
        ChatSummary cached = org.mockito.Mockito.mock(ChatSummary.class);

        when(relationship.getId()).thenReturn(1L);
        when(room.getId()).thenReturn(20L);
        when(lastMessage.getId()).thenReturn(30L);
        when(cached.getLastMessageId()).thenReturn(30L);
        when(cached.getCacheVersion()).thenReturn(2);
        when(cached.getSummary()).thenReturn("저장된 요약");
        when(relationshipRepository
                .findByCharacterIdAndMemberIdAndCharacterDeletedAtIsNull(10L, 2L))
                .thenReturn(Optional.of(relationship));
        when(chatRoomRepository.findByRelationshipId(1L)).thenReturn(Optional.of(room));
        when(chatMessageRepository.findTopByChatRoomIdOrderByIdDesc(any(Long.class)))
                .thenReturn(Optional.of(lastMessage));
        when(chatSummaryRepository.findByRelationshipId(1L)).thenReturn(Optional.of(cached));

        assertThat(service.getSummary(2L, 10L).summary()).isEqualTo("저장된 요약");
        verify(aiServerClient, never()).summarize(any());
    }

    @Test
    void generatesAndStoresSummaryWhenConversationChanged() {
        Relationship relationship = org.mockito.Mockito.mock(Relationship.class);
        ChatRoom room = org.mockito.Mockito.mock(ChatRoom.class);
        ChatMessage userMessage = org.mockito.Mockito.mock(ChatMessage.class);
        ChatMessage lastMessage = org.mockito.Mockito.mock(ChatMessage.class);
        Member member = org.mockito.Mockito.mock(Member.class);
        Character character = org.mockito.Mockito.mock(Character.class);

        when(relationship.getId()).thenReturn(1L);
        when(relationship.getCharacter()).thenReturn(character);
        when(member.getFirstName()).thenReturn("현경");
        when(character.getFirstName()).thenReturn("민준");
        when(room.getId()).thenReturn(20L);
        when(userMessage.getContent()).thenReturn("아이스티가 좋아");
        when(userMessage.getSenderType()).thenReturn(SenderType.USER);
        when(lastMessage.getId()).thenReturn(31L);
        when(lastMessage.getContent()).thenReturn("나도 좋아해");
        when(lastMessage.getSenderType()).thenReturn(SenderType.AI);
        when(relationshipRepository
                .findByCharacterIdAndMemberIdAndCharacterDeletedAtIsNull(10L, 2L))
                .thenReturn(Optional.of(relationship));
        when(chatRoomRepository.findByRelationshipId(1L)).thenReturn(Optional.of(room));
        when(chatMessageRepository.findTopByChatRoomIdOrderByIdDesc(any(Long.class)))
                .thenReturn(Optional.of(lastMessage));
        when(chatSummaryRepository.findByRelationshipId(1L)).thenReturn(Optional.empty());
        when(memberRepository.findById(2L)).thenReturn(Optional.of(member));
        // 저장소는 최신순으로 반환하고, 서비스가 AI 요청 전에 시간순으로 뒤집는다.
        when(chatMessageRepository.findRecent(any(Long.class), any(Pageable.class)))
                .thenReturn(List.of(lastMessage, userMessage));
        when(aiServerClient.summarize(any()))
                .thenReturn(new AiSummaryResponse(" 아이스티를 좋아해요. "));

        assertThat(service.getSummary(2L, 10L).summary()).isEqualTo("아이스티를 좋아해요.");

        ArgumentCaptor<AiSummaryRequest> requestCaptor =
                ArgumentCaptor.forClass(AiSummaryRequest.class);
        verify(aiServerClient).summarize(requestCaptor.capture());
        assertThat(requestCaptor.getValue().messages())
                .extracting(message -> message.role())
                .containsExactly("user", "assistant");
        assertThat(requestCaptor.getValue().userName()).isEqualTo("현경");
        assertThat(requestCaptor.getValue().characterName()).isEqualTo("민준");
        verify(chatSummaryRepository).save(any(ChatSummary.class));
    }

    @Test
    void regeneratesSummaryWhenCacheVersionIsOld() {
        Relationship relationship = org.mockito.Mockito.mock(Relationship.class);
        ChatRoom room = org.mockito.Mockito.mock(ChatRoom.class);
        ChatMessage lastMessage = org.mockito.Mockito.mock(ChatMessage.class);
        ChatSummary cached = org.mockito.Mockito.mock(ChatSummary.class);
        Member member = org.mockito.Mockito.mock(Member.class);
        Character character = org.mockito.Mockito.mock(Character.class);

        when(relationship.getId()).thenReturn(1L);
        when(relationship.getCharacter()).thenReturn(character);
        when(room.getId()).thenReturn(20L);
        when(lastMessage.getId()).thenReturn(30L);
        when(lastMessage.getContent()).thenReturn("안녕");
        when(lastMessage.getSenderType()).thenReturn(SenderType.USER);
        when(cached.getLastMessageId()).thenReturn(30L);
        when(cached.getCacheVersion()).thenReturn(null);
        when(member.getFirstName()).thenReturn("현경");
        when(character.getFirstName()).thenReturn("민준");
        when(relationshipRepository
                .findByCharacterIdAndMemberIdAndCharacterDeletedAtIsNull(10L, 2L))
                .thenReturn(Optional.of(relationship));
        when(chatRoomRepository.findByRelationshipId(1L)).thenReturn(Optional.of(room));
        when(chatMessageRepository.findTopByChatRoomIdOrderByIdDesc(any(Long.class)))
                .thenReturn(Optional.of(lastMessage));
        when(chatSummaryRepository.findByRelationshipId(1L)).thenReturn(Optional.of(cached));
        when(memberRepository.findById(2L)).thenReturn(Optional.of(member));
        when(chatMessageRepository.findRecent(any(Long.class), any(Pageable.class)))
                .thenReturn(List.of(lastMessage));
        when(aiServerClient.summarize(any())).thenReturn(new AiSummaryResponse("새 요약"));

        assertThat(service.getSummary(2L, 10L).summary()).isEqualTo("새 요약");
        verify(aiServerClient).summarize(any());
        verify(cached).update("새 요약", 30L, 2);
    }

    @Test
    void sendsNonBlankFallbackNamesWhenParticipantNamesAreMissing() {
        Relationship relationship = org.mockito.Mockito.mock(Relationship.class);
        ChatRoom room = org.mockito.Mockito.mock(ChatRoom.class);
        ChatMessage lastMessage = org.mockito.Mockito.mock(ChatMessage.class);
        ChatSummary cached = org.mockito.Mockito.mock(ChatSummary.class);
        Member member = org.mockito.Mockito.mock(Member.class);
        Character character = org.mockito.Mockito.mock(Character.class);

        when(relationship.getId()).thenReturn(1L);
        when(relationship.getCharacter()).thenReturn(character);
        when(room.getId()).thenReturn(20L);
        when(lastMessage.getId()).thenReturn(30L);
        when(lastMessage.getContent()).thenReturn("안녕");
        when(lastMessage.getSenderType()).thenReturn(SenderType.USER);
        when(cached.getLastMessageId()).thenReturn(30L);
        when(cached.getCacheVersion()).thenReturn(null);
        when(member.getFirstName()).thenReturn(" ");
        when(member.getLastName()).thenReturn("김");
        when(character.getFirstName()).thenReturn(null);
        when(character.getLastName()).thenReturn(null);
        when(relationshipRepository
                .findByCharacterIdAndMemberIdAndCharacterDeletedAtIsNull(10L, 2L))
                .thenReturn(Optional.of(relationship));
        when(chatRoomRepository.findByRelationshipId(1L)).thenReturn(Optional.of(room));
        when(chatMessageRepository.findTopByChatRoomIdOrderByIdDesc(any(Long.class)))
                .thenReturn(Optional.of(lastMessage));
        when(chatSummaryRepository.findByRelationshipId(1L)).thenReturn(Optional.of(cached));
        when(memberRepository.findById(2L)).thenReturn(Optional.of(member));
        when(chatMessageRepository.findRecent(any(Long.class), any(Pageable.class)))
                .thenReturn(List.of(lastMessage));
        when(aiServerClient.summarize(any())).thenReturn(new AiSummaryResponse("새 요약"));

        service.getSummary(2L, 10L);

        ArgumentCaptor<AiSummaryRequest> requestCaptor =
                ArgumentCaptor.forClass(AiSummaryRequest.class);
        verify(aiServerClient).summarize(requestCaptor.capture());
        assertThat(requestCaptor.getValue().userName()).isEqualTo("김");
        assertThat(requestCaptor.getValue().characterName()).isEqualTo("상대방");
    }
}
