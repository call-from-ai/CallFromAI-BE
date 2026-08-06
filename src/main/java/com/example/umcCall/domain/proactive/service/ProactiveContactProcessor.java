package com.example.umcCall.domain.proactive.service;

import com.example.umcCall.domain.ai.client.AiServerClient;
import com.example.umcCall.domain.ai.dto.AiChatHistoryItem;
import com.example.umcCall.domain.ai.dto.AiChatResponse;
import com.example.umcCall.domain.ai.dto.AiProactiveRequest;
import com.example.umcCall.domain.ai.enums.AiConversationChannel;
import com.example.umcCall.domain.ai.exception.AiErrorCode;
import com.example.umcCall.domain.ai.exception.AiServerException;
import com.example.umcCall.domain.ai.mapper.AiCharacterSnapshotMapper;
import com.example.umcCall.domain.ai.mapper.AiRelationshipSnapshotMapper;
import com.example.umcCall.domain.call.enums.CallStatus;
import com.example.umcCall.domain.call.repository.CallRepository;
import com.example.umcCall.domain.call.service.ImmediateAiCallService;
import com.example.umcCall.domain.character.entity.CharacterAiProfile;
import com.example.umcCall.domain.character.repository.CharacterAiProfileRepository;
import com.example.umcCall.domain.chat.entity.ChatMessage;
import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.enums.MessageType;
import com.example.umcCall.domain.chat.enums.SenderType;
import com.example.umcCall.domain.chat.repository.ChatMessageRepository;
import com.example.umcCall.domain.chat.repository.ChatRoomRepository;
import com.example.umcCall.domain.chat.service.ChatMessageNotifier;
import com.example.umcCall.domain.proactive.entity.ProactiveContactSchedule;
import com.example.umcCall.domain.proactive.enums.AttachmentLevel;
import com.example.umcCall.domain.proactive.enums.ProactiveAction;
import com.example.umcCall.domain.proactive.enums.ProactiveRelationshipState;
import com.example.umcCall.domain.proactive.enums.RecentResponse;
import com.example.umcCall.domain.proactive.repository.ProactiveContactScheduleRepository;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.entity.RelationshipStatus;
import com.example.umcCall.domain.relationship.repository.RelationshipStatusRepository;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class ProactiveContactProcessor {

    private static final int DAILY_CONTACT_LIMIT = 10;
    private static final int DAILY_CALL_LIMIT = 3;
    private static final String PROACTIVE_SEED =
            "The user has not sent a new message. Send one short proactive check-in.";

    private final ProactiveContactScheduleRepository scheduleRepository;
    private final CharacterAiProfileRepository profileRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository messageRepository;
    private final RelationshipStatusRepository relationshipStatusRepository;
    private final CallRepository callRepository;
    private final ImmediateAiCallService immediateAiCallService;
    private final ProactiveContactPolicy policy;
    private final PreferredContactTimePolicy preferredTimePolicy;
    private final RelationshipStateResolver stateResolver;
    private final AiCharacterSnapshotMapper characterSnapshotMapper;
    private final AiRelationshipSnapshotMapper relationshipSnapshotMapper;
    private final AiServerClient aiServerClient;
    private final ChatMessageNotifier chatMessageNotifier;

    @Transactional
    public Claim claim(Long scheduleId, LocalDateTime now) {
        ProactiveContactSchedule schedule = scheduleRepository.findByIdForUpdate(scheduleId).orElse(null);
        if (schedule == null || !schedule.isEnabled()) return null;

        if (schedule.getPendingRequestId() != null) {
            if (schedule.getPendingRetryAt() != null && !schedule.getPendingRetryAt().isAfter(now)) {
                return new Claim(
                        schedule.getId(),
                        schedule.getPendingRequestId(),
                        schedule.getPendingAction());
            }
            return null;
        }
        if (schedule.getNextCheckAt() == null || schedule.getNextCheckAt().isAfter(now)) return null;

        Relationship relationship = schedule.getRelationship();
        if (relationship.getCharacter().getDeletedAt() != null) {
            schedule.disable();
            return null;
        }

        ChatRoom room = chatRoomRepository.findByRelationshipId(relationship.getId()).orElse(null);
        LocalDateTime lastContactAt = room == null ? null : room.getLastMessageAt();
        if (schedule.isAwaitingUserResponse() && room != null
                && schedule.getLastProactiveContactAt() != null) {
            boolean userResponded = messageRepository
                    .findTopByChatRoomIdAndSenderTypeOrderByIdDesc(room.getId(), SenderType.USER)
                    .map(message -> message.getCreatedAt().isAfter(schedule.getLastProactiveContactAt()))
                    .orElse(false);
            if (userResponded) {
                schedule.recordUserResponse(schedule.getNextCheckAt());
            } else {
                schedule.recordNoResponse();
            }
        }
        CharacterAiProfile profile = profileRepository.findById(relationship.getCharacter().getId()).orElse(null);
        boolean activeCall = callRepository.existsByRelationshipIdAndStatusIn(
                relationship.getId(), CallStatus.ACTIVE);

        ProactiveRelationshipState state = stateResolver.resolve(relationship.getEmotion());
        RecentResponse recentResponse = schedule.getConsecutiveNoResponseCount() > 0
                ? RecentResponse.NO_RESPONSE
                : RecentResponse.POSITIVE;
        ProactiveContactPolicy.Context context = new ProactiveContactPolicy.Context(
                now,
                lastContactAt,
                schedule.getPausedUntil(),
                schedule.isEnabled(),
                false,
                false,
                activeCall,
                schedule.dailyCountOn(now.toLocalDate()),
                DAILY_CONTACT_LIMIT,
                schedule.dailyCallCountOn(now.toLocalDate()),
                DAILY_CALL_LIMIT,
                relationship.getCharacter().getPreferTime(),
                AttachmentLevel.from(profile == null ? null : profile.getAttachment()),
                state,
                recentResponse,
                schedule.getConsecutiveNoResponseCount(),
                false,
                false,
                relationship.isMain(),
                false,
                null);
        ProactiveContactPolicy.Decision decision = policy.decide(context);
        if (decision.action() == ProactiveAction.BLOCKED) {
            schedule.deferUntil(null);
            return null;
        }
        if (decision.action() == ProactiveAction.DEFER) {
            schedule.deferUntil(decision.nextCheckAt());
            return null;
        }

        String requestId = "proactive-" + UUID.randomUUID();
        schedule.claim(requestId, decision.action(), decision.contactReason());
        return new Claim(schedule.getId(), requestId, decision.action());
    }

    /**
     * 스케줄러 테스트용 claim. 시간 간격 정책은 우회하지만 활성 관계·삭제·enabled는 검증한다.
     */
    @Transactional
    public Claim forceClaimForDebug(Long scheduleId) {
        return forceClaimForDebug(scheduleId, ProactiveAction.CHAT, "proactive-debug-", "DEBUG_FORCE_SEND");
    }

    /**
     * 스케줄러 테스트용 통화 claim. 스케줄 정책과 통화 선택 확률은 우회한다.
     */
    @Transactional
    public Claim forceCallClaimForDebug(Long scheduleId) {
        return forceClaimForDebug(scheduleId, ProactiveAction.CALL,
                "proactive-debug-call-", "DEBUG_FORCE_CALL");
    }

    private Claim forceClaimForDebug(Long scheduleId, ProactiveAction action,
                                     String requestIdPrefix, String contactReason) {
        ProactiveContactSchedule schedule = scheduleRepository.findByIdForUpdate(scheduleId).orElse(null);
        if (schedule == null || !schedule.isEnabled()) return null;
        if (schedule.getPendingRequestId() != null) return null;
        Relationship relationship = schedule.getRelationship();
        if (!relationship.isMain() || relationship.getCharacter().getDeletedAt() != null) return null;
        String requestId = requestIdPrefix + UUID.randomUUID();
        schedule.claim(requestId, action, contactReason);
        return new Claim(schedule.getId(), requestId, action);
    }

    @Transactional(readOnly = true)
    public String generate(Claim claim) {
        if (claim.action() != ProactiveAction.CHAT) return null;
        ProactiveContactSchedule schedule = scheduleRepository.findById(claim.scheduleId()).orElseThrow();
        if (!claim.requestId().equals(schedule.getPendingRequestId())) return null;

        Relationship relationship = schedule.getRelationship();
        CharacterAiProfile profile = profileRepository.findById(relationship.getCharacter().getId()).orElseThrow();
        RelationshipStatus status = relationshipStatusRepository.findByRelationshipId(relationship.getId())
                .orElseThrow();
        ChatRoom room = chatRoomRepository.findByRelationshipId(relationship.getId()).orElseThrow();

        List<ChatMessage> recent = messageRepository.findRecent(room.getId(), PageRequest.of(0, 20));
        List<AiChatHistoryItem> history = recent.stream()
                .map(message -> new AiChatHistoryItem(
                        message.getSenderType() == SenderType.USER ? "user" : "assistant",
                        message.getContent(),
                        message.getCreatedAt()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        Collections.reverse(history);

        ProactiveRelationshipState state = stateResolver.resolve(relationship.getEmotion());
        RecentResponse response = schedule.getConsecutiveNoResponseCount() > 0
                ? RecentResponse.NO_RESPONSE : RecentResponse.POSITIVE;
        AiProactiveRequest request = new AiProactiveRequest(
                claim.requestId(),
                relationship.getCharacter().getId(),
                AiConversationChannel.CHAT,
                PROACTIVE_SEED,
                aiContactReason(schedule.getPendingContactReason()),
                state.name(),
                response.name(),
                characterSnapshotMapper.toSnapshot(relationship.getCharacter(), profile, relationship),
                relationshipSnapshotMapper.toSnapshot(relationship, status),
                history);

        AiChatResponse aiResponse = aiServerClient.proactive(request);
        return aiResponse.reply();
    }

    /**
     * 메인 서버의 세부 스케줄 사유를 AI 서버가 지원하는 공개 계약으로 축약한다.
     * 관계 상태별 표현은 relationshipState가 별도로 전달하므로 일반 채팅은 NORMAL_CHECK_IN이면 충분하다.
     */
    private String aiContactReason(String internalReason) {
        return "CALL_OFFER".equals(internalReason) ? "CALL_OFFER" : "NORMAL_CHECK_IN";
    }

    @Transactional
    public void complete(Claim claim, String reply, LocalDateTime now) {
        ProactiveContactSchedule schedule = scheduleRepository.findByIdForUpdate(claim.scheduleId()).orElseThrow();
        if (!claim.requestId().equals(schedule.getPendingRequestId())) return;

        Relationship relationship = schedule.getRelationship();
        ChatRoom room = chatRoomRepository.findByRelationshipId(relationship.getId()).orElseThrow();
        if (!messageRepository.existsByProactiveRequestId(claim.requestId())) {
            ChatMessage saved = messageRepository.save(ChatMessage.builder()
                    .senderType(SenderType.AI)
                    .content(reply)
                    .messageType(MessageType.TEXT)
                    .read(false)
                    .chatRoom(room)
                    .proactiveRequestId(claim.requestId())
                    .build());
            room.updateLastMessageAt(now);
            room.reveal();
            pushToClient(room, saved);
        }

        CharacterAiProfile profile = profileRepository.findById(relationship.getCharacter().getId()).orElseThrow();
        ProactiveRelationshipState state = stateResolver.resolve(relationship.getEmotion());
        LocalDateTime next = policy.nextCandidate(now, profile.getAttachment(), state);
        schedule.complete(now, preferredTimePolicy.adjustCandidate(
                relationship.getCharacter().getPreferTime(), next));
    }

    /**
     * 저장된 선제 메시지를 커밋이 확정된 뒤에 유저에게 배달한다(접속 중이면 SSE, 아니면 FCM).
     * 트랜잭션 안에서 바로 배달하면 이후 롤백 시 DB엔 없는 유령 알림이 나가므로 afterCommit으로 미룬다.
     */
    private void pushToClient(ChatRoom room, ChatMessage message) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                chatMessageNotifier.notify(room, message);
            }
        });
    }

    @Transactional
    public boolean completeCall(Claim claim, LocalDateTime now) {
        return completeCall(claim, now, now.plusMinutes(10));
    }

    /** 강제 통화 테스트 처리. 생성 실패 시 같은 트랜잭션에서 디버그 호출 전 시각을 복구한다. */
    @Transactional
    public boolean completeCallForDebug(Claim claim, LocalDateTime now,
                                        LocalDateTime previousNextCheckAt) {
        return completeCall(claim, now, previousNextCheckAt);
    }

    private boolean completeCall(Claim claim, LocalDateTime now,
                                 LocalDateTime nextCheckAtOnFailure) {
        ProactiveContactSchedule schedule = scheduleRepository.findByIdForUpdate(claim.scheduleId()).orElseThrow();
        if (!claim.requestId().equals(schedule.getPendingRequestId())
                || schedule.getPendingAction() != ProactiveAction.CALL) {
            return false;
        }

        Relationship relationship = schedule.getRelationship();
        if (!immediateAiCallService.ring(relationship.getId())) {
            schedule.releaseClaim(nextCheckAtOnFailure);
            return false;
        }

        CharacterAiProfile profile = profileRepository.findById(relationship.getCharacter().getId()).orElseThrow();
        ProactiveRelationshipState state = stateResolver.resolve(relationship.getEmotion());
        LocalDateTime next = policy.nextCandidate(now, profile.getAttachment(), state);
        schedule.completeCall(now, preferredTimePolicy.adjustCandidate(
                relationship.getCharacter().getPreferTime(), next));
        return true;
    }

    /** 강제 통화 테스트 예외를 기록하고 디버그 호출 전 스케줄 시각을 한 트랜잭션에서 복구한다. */
    @Transactional
    public void recordDebugCallFailure(Claim claim, RuntimeException exception,
                                       LocalDateTime previousNextCheckAt) {
        ProactiveContactSchedule schedule = scheduleRepository.findByIdForUpdate(claim.scheduleId()).orElse(null);
        if (schedule != null && claim.requestId().equals(schedule.getPendingRequestId())) {
            schedule.recordDebugCallFailure(exception, previousNextCheckAt);
        }
    }

    @Transactional
    public void fail(Claim claim, RuntimeException exception, LocalDateTime now) {
        ProactiveContactSchedule schedule = scheduleRepository.findByIdForUpdate(claim.scheduleId()).orElse(null);
        if (schedule != null && claim.requestId().equals(schedule.getPendingRequestId())) {
            if (exception instanceof AiServerException aiException
                    && aiException.getErrorCode() == AiErrorCode.DUPLICATE_REQUEST) {
                schedule.retryWithNewRequest(now);
            } else {
                schedule.retry(exception, now);
            }
        }
    }

    public record Claim(Long scheduleId, String requestId, ProactiveAction action) {
    }
}
