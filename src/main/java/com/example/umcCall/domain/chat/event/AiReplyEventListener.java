package com.example.umcCall.domain.chat.event;

import com.example.umcCall.domain.ai.dto.AiChatHistoryItem;
import com.example.umcCall.domain.chat.dto.response.ChatMessageResponse;
import com.example.umcCall.domain.chat.entity.ChatMessage;
import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.repository.ChatMessageRepository;
import com.example.umcCall.domain.chat.repository.ChatRoomRepository;
import com.example.umcCall.domain.chat.service.AiReplyGenerator;
import com.example.umcCall.domain.chat.service.ChatMessageService;
import com.example.umcCall.domain.chat.service.ChatSseService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 유저 메시지 전송 커밋 후, 비동기로 AI 답장을 생성해 SSE로 전달한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiReplyEventListener {

    private static final int HISTORY_SIZE = 20;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AiReplyGenerator aiReplyGenerator;
    private final ChatMessageService chatMessageService;
    private final ChatSseService chatSseService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserMessageSent(UserMessageSentEvent event) {
        try {
            ChatRoom room = chatRoomRepository.findById(event.chatRoomId()).orElse(null);
            if (room == null || room.getRelationshipId() == null) {
                return;
            }

            List<AiChatHistoryItem> history = buildHistory(event.chatRoomId(), event.userMessageId());
            String reply = aiReplyGenerator.generateReply(room.getRelationshipId(), event.userMessage(), history);
            if (reply == null || reply.isBlank()) {
                return;
            }

            ChatMessage aiMessage = chatMessageService.saveAiMessage(event.chatRoomId(), reply);
            chatSseService.sendToMember(event.memberId(), "message", ChatMessageResponse.from(aiMessage));
        } catch (Exception e) {
            log.error("AI 답장 처리 실패. chatRoomId={}", event.chatRoomId(), e);
        }
    }

    /** 현재 메시지 이전의 최근 대화 이력을 과거→최신 순으로 만든다. */
    private List<AiChatHistoryItem> buildHistory(Long chatRoomId, Long beforeMessageId) {
        List<ChatMessage> recent = new ArrayList<>(chatMessageRepository.findMessagesByCursor(
                chatRoomId, null, beforeMessageId, PageRequest.of(0, HISTORY_SIZE)));
        Collections.reverse(recent);   // DESC → ASC(과거→최신)
        return recent.stream()
                .map(m -> new AiChatHistoryItem(m.getSenderType().name(), m.getContent(), m.getCreatedAt()))
                .toList();
    }
}
