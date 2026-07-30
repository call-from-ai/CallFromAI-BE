package com.example.umcCall.domain.relationship.service;

import com.example.umcCall.domain.ai.client.AiServerClient;
import com.example.umcCall.domain.ai.dto.AiSummaryMessage;
import com.example.umcCall.domain.ai.dto.AiSummaryRequest;
import com.example.umcCall.domain.character.exception.CharacterErrorCode;
import com.example.umcCall.domain.chat.entity.ChatMessage;
import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.enums.SenderType;
import com.example.umcCall.domain.chat.exception.ChatErrorCode;
import com.example.umcCall.domain.chat.repository.ChatMessageRepository;
import com.example.umcCall.domain.chat.repository.ChatRoomRepository;
import com.example.umcCall.domain.member.entity.Member;
import com.example.umcCall.domain.member.exception.MemberErrorCode;
import com.example.umcCall.domain.member.repository.MemberRepository;
import com.example.umcCall.domain.relationship.dto.response.ChatSummaryResponse;
import com.example.umcCall.domain.relationship.entity.ChatSummary;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.repository.ChatSummaryRepository;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import com.example.umcCall.global.exception.BaseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatSummaryService {

    private static final int MAX_SUMMARY_CHARACTERS = 200;
    private static final int MAX_MESSAGES = 200;
    // 요약 프롬프트/DTO의 의미가 바뀌면 증가시켜 기존 캐시를 자동 재생성한다.
    private static final int SUMMARY_CACHE_VERSION = 2;
    private static final String EMPTY_CONVERSATION_SUMMARY = "아직 나눈 대화가 없어요.";
    private static final Object[] SUMMARY_LOCKS = createSummaryLocks(256);

    private final RelationshipRepository relationshipRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSummaryRepository chatSummaryRepository;
    private final MemberRepository memberRepository;
    private final AiServerClient aiServerClient;

    public ChatSummaryResponse getSummary(Long memberId, Long characterId) {
        Relationship relationship = relationshipRepository
                .findByCharacterIdAndMemberIdAndCharacterDeletedAtIsNull(characterId, memberId)
                .orElseThrow(() -> new BaseException(
                        CharacterErrorCode.CHARACTER_NOT_FOUND));
        Long relationshipId = relationship.getId();

        ChatRoom room = chatRoomRepository.findByRelationshipId(relationshipId)
                .orElseThrow(() -> new BaseException(ChatErrorCode.CHATROOM_NOT_FOUND));

        LocalDateTime todayStartedAt = LocalDate.now().atStartOfDay();
        ChatMessage lastMessage = chatMessageRepository
                .findTopByChatRoomIdAndDeletedFalseAndCreatedAtBeforeOrderByIdDesc(
                        room.getId(), todayStartedAt)
                .orElse(null);
        if (lastMessage == null) {
            return new ChatSummaryResponse(EMPTY_CONVERSATION_SUMMARY);
        }

        // 동일 관계의 캐시 미스가 겹쳐 중복 AI 호출/INSERT가 발생하지 않도록 직렬화한다.
        synchronized (summaryLock(relationshipId)) {
            return getOrCreateSummary(
                    memberId, relationship, relationshipId, room, lastMessage, todayStartedAt);
        }
    }

    private ChatSummaryResponse getOrCreateSummary(
            Long memberId,
            Relationship relationship,
            Long relationshipId,
            ChatRoom room,
            ChatMessage lastMessage,
            LocalDateTime todayStartedAt) {
        ChatSummary cached = chatSummaryRepository.findByRelationshipId(relationshipId)
                .orElse(null);
        if (cached != null
                && lastMessage.getId().equals(cached.getLastMessageId())
                && Integer.valueOf(SUMMARY_CACHE_VERSION).equals(cached.getCacheVersion())) {
            return new ChatSummaryResponse(cached.getSummary());
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BaseException(MemberErrorCode.MEMBER_NOT_FOUND));

        List<ChatMessage> recent = new ArrayList<>(chatMessageRepository.findRecentBefore(
                room.getId(), todayStartedAt, PageRequest.of(0, MAX_MESSAGES)));
        Collections.reverse(recent);

        List<AiSummaryMessage> messages = recent.stream()
                .filter(message -> message.getContent() != null
                        && !message.getContent().isBlank())
                .map(message -> new AiSummaryMessage(
                        toRole(message.getSenderType()), message.getContent()))
                .toList();

        String previousSummary = cached == null ? null : cached.getSummary();
        String generated = aiServerClient.summarize(new AiSummaryRequest(
                relationshipId,
                resolveParticipantName(member.getFirstName(), member.getLastName(), "사용자"),
                resolveParticipantName(
                        relationship.getCharacter().getFirstName(),
                        relationship.getCharacter().getLastName(),
                        "상대방"),
                previousSummary,
                messages,
                MAX_SUMMARY_CHARACTERS
        )).summary();
        String summary = limitCodePoints(generated, MAX_SUMMARY_CHARACTERS);

        if (cached == null) {
            cached = ChatSummary.create(
                    relationship, summary, lastMessage.getId(), SUMMARY_CACHE_VERSION);
        } else {
            cached.update(summary, lastMessage.getId(), SUMMARY_CACHE_VERSION);
        }
        try {
            chatSummaryRepository.save(cached);
        } catch (DataIntegrityViolationException duplicateInsert) {
            // 다중 인스턴스에서 동시에 최초 생성된 경우 DB unique 제약의 승자 캐시를 사용한다.
            ChatSummary winner = chatSummaryRepository.findByRelationshipId(relationshipId)
                    .orElseThrow(() -> duplicateInsert);
            return new ChatSummaryResponse(winner.getSummary());
        }

        return new ChatSummaryResponse(summary);
    }

    private static Object[] createSummaryLocks(int size) {
        Object[] locks = new Object[size];
        for (int i = 0; i < size; i++) {
            locks[i] = new Object();
        }
        return locks;
    }

    private Object summaryLock(Long relationshipId) {
        return SUMMARY_LOCKS[Math.floorMod(relationshipId.hashCode(), SUMMARY_LOCKS.length)];
    }

    private String toRole(SenderType senderType) {
        return senderType == SenderType.USER ? "user" : "assistant";
    }

    /**
     * AI 요약 API는 참여자 이름에 non-blank 계약을 적용한다.
     * 표시 규약인 firstName을 우선하고 레거시/온보딩 미완료 데이터에는 안전한 대체값을 사용한다.
     */
    private String resolveParticipantName(String firstName, String lastName, String fallback) {
        if (firstName != null && !firstName.isBlank()) {
            return firstName.strip();
        }
        if (lastName != null && !lastName.isBlank()) {
            return lastName.strip();
        }
        return fallback;
    }

    private String limitCodePoints(String text, int maxLength) {
        String value = text.strip();
        int count = value.codePointCount(0, value.length());
        if (count <= maxLength) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxLength));
    }
}
