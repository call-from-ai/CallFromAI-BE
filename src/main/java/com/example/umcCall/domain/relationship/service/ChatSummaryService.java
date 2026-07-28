package com.example.umcCall.domain.relationship.service;

import com.example.umcCall.domain.ai.client.AiServerClient;
import com.example.umcCall.domain.ai.dto.AiSummaryMessage;
import com.example.umcCall.domain.ai.dto.AiSummaryRequest;
import com.example.umcCall.domain.chat.entity.ChatMessage;
import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.enums.SenderType;
import com.example.umcCall.domain.chat.exception.ChatErrorCode;
import com.example.umcCall.domain.chat.repository.ChatMessageRepository;
import com.example.umcCall.domain.chat.repository.ChatRoomRepository;
import com.example.umcCall.domain.relationship.dto.response.ChatSummaryResponse;
import com.example.umcCall.domain.relationship.entity.ChatSummary;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.exception.RelationshipErrorCode;
import com.example.umcCall.domain.relationship.repository.ChatSummaryRepository;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import com.example.umcCall.global.exception.BaseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatSummaryService {

    private static final int MAX_SUMMARY_CHARACTERS = 200;
    private static final int MAX_MESSAGES = 200;
    private static final String EMPTY_CONVERSATION_SUMMARY = "아직 나눈 대화가 없어요.";

    private final RelationshipRepository relationshipRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSummaryRepository chatSummaryRepository;
    private final AiServerClient aiServerClient;

    @Transactional
    public ChatSummaryResponse getSummary(Long memberId, Long relationshipId) {
        Relationship relationship = relationshipRepository
                .findByIdAndMemberIdAndCharacterDeletedAtIsNull(relationshipId, memberId)
                .orElseThrow(() -> new BaseException(
                        RelationshipErrorCode.RELATIONSHIP_NOT_FOUND));

        ChatRoom room = chatRoomRepository.findByRelationshipId(relationshipId)
                .orElseThrow(() -> new BaseException(ChatErrorCode.CHATROOM_NOT_FOUND));

        ChatMessage lastMessage = chatMessageRepository
                .findTopByChatRoomIdAndDeletedFalseOrderByIdDesc(room.getId())
                .orElse(null);
        if (lastMessage == null) {
            return new ChatSummaryResponse(EMPTY_CONVERSATION_SUMMARY);
        }

        ChatSummary cached = chatSummaryRepository.findByRelationshipId(relationshipId)
                .orElse(null);
        if (cached != null && lastMessage.getId().equals(cached.getLastMessageId())) {
            return new ChatSummaryResponse(cached.getSummary());
        }

        List<ChatMessage> recent = new ArrayList<>(chatMessageRepository.findRecent(
                room.getId(), PageRequest.of(0, MAX_MESSAGES)));
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
                previousSummary,
                messages,
                MAX_SUMMARY_CHARACTERS
        )).summary();
        String summary = limitCodePoints(generated, MAX_SUMMARY_CHARACTERS);

        if (cached == null) {
            cached = ChatSummary.create(relationship, summary, lastMessage.getId());
        } else {
            cached.update(summary, lastMessage.getId());
        }
        chatSummaryRepository.save(cached);

        return new ChatSummaryResponse(summary);
    }

    private String toRole(SenderType senderType) {
        return senderType == SenderType.USER ? "user" : "assistant";
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
