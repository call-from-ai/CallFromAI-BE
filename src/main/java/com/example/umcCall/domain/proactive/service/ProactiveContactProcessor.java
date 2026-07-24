package com.example.umcCall.domain.proactive.service;

import com.example.umcCall.domain.ai.client.AiServerClient;
import com.example.umcCall.domain.ai.dto.AiChatHistoryItem;
import com.example.umcCall.domain.ai.dto.AiChatResponse;
import com.example.umcCall.domain.ai.dto.AiProactiveRequest;
import com.example.umcCall.domain.ai.mapper.AiCharacterSnapshotMapper;
import com.example.umcCall.domain.ai.mapper.AiRelationshipSnapshotMapper;
import com.example.umcCall.domain.call.enums.CallStatus;
import com.example.umcCall.domain.call.repository.CallRepository;
import com.example.umcCall.domain.character.entity.CharacterAiProfile;
import com.example.umcCall.domain.character.repository.CharacterAiProfileRepository;
import com.example.umcCall.domain.chat.entity.ChatMessage;
import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.enums.MessageType;
import com.example.umcCall.domain.chat.enums.SenderType;
import com.example.umcCall.domain.chat.repository.ChatMessageRepository;
import com.example.umcCall.domain.chat.repository.ChatRoomRepository;
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
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProactiveContactProcessor {

    private static final int DAILY_LIMIT = 3;
    private static final String PROACTIVE_SEED =
            "The user has not sent a new message. Send one short proactive check-in.";

    private final ProactiveContactScheduleRepository scheduleRepository;
    private final CharacterAiProfileRepository profileRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository messageRepository;
    private final RelationshipStatusRepository relationshipStatusRepository;
    private final CallRepository callRepository;
    private final ProactiveContactPolicy policy;
    private final PreferredContactTimePolicy preferredTimePolicy;
    private final RelationshipStateResolver stateResolver;
    private final AiCharacterSnapshotMapper characterSnapshotMapper;
    private final AiRelationshipSnapshotMapper relationshipSnapshotMapper;
    private final AiServerClient aiServerClient;

    @Transactional
    public Claim claim(Long scheduleId, LocalDateTime now) {
        ProactiveContactSchedule schedule = scheduleRepository.findByIdForUpdate(scheduleId).orElse(null);
        if (schedule == null || !schedule.isEnabled()) return null;

        if (schedule.getPendingRequestId() != null) {
            if (schedule.getPendingRetryAt() != null && !schedule.getPendingRetryAt().isAfter(now)) {
                return new Claim(schedule.getId(), schedule.getPendingRequestId());
            }
            return null;
        }
        if (schedule.getNextCheckAt() == null || schedule.getNextCheckAt().isAfter(now)) return null;

        Relationship relationship = schedule.getRelationship();
        if (!relationship.isMain() || relationship.getCharacter().getDeletedAt() != null) {
            schedule.disable();
            return null;
        }

        ChatRoom room = chatRoomRepository.findByRelationshipId(relationship.getId()).orElse(null);
        LocalDateTime lastContactAt = room == null ? null : room.getLastMessageAt();
        if (schedule.isAwaitingUserResponse() && room != null
                && schedule.getLastProactiveContactAt() != null) {
            boolean userResponded = messageRepository
                    .findTopByChatRoomIdAndSenderTypeAndDeletedFalseOrderByIdDesc(room.getId(), SenderType.USER)
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
                relationship.getId(), EnumSet.of(CallStatus.DIALING, CallStatus.RINGING, CallStatus.IN_PROGRESS));

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
                DAILY_LIMIT,
                relationship.getCharacter().getPreferTime(),
                AttachmentLevel.from(profile == null ? null : profile.getAttachment()),
                state,
                recentResponse,
                schedule.getConsecutiveNoResponseCount(),
                false,
                false,
                false,
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
        // 전화 실행기가 완성되기 전에는 CALL 후보도 통화 제안 채팅으로 보낸다.
        schedule.claim(requestId, ProactiveAction.CHAT, decision.contactReason());
        return new Claim(schedule.getId(), requestId);
    }

    /**
     * local Swagger 테스트 전용 claim. 시간 간격 정책은 우회하지만 활성 관계·삭제·enabled는 검증한다.
     */
    @Transactional
    public Claim forceClaimForDebug(Long scheduleId) {
        ProactiveContactSchedule schedule = scheduleRepository.findByIdForUpdate(scheduleId).orElse(null);
        if (schedule == null || !schedule.isEnabled()) return null;
        if (schedule.getPendingRequestId() != null) return null;
        Relationship relationship = schedule.getRelationship();
        if (!relationship.isMain() || relationship.getCharacter().getDeletedAt() != null) return null;
        String requestId = "proactive-debug-" + UUID.randomUUID();
        schedule.claim(requestId, ProactiveAction.CHAT, "DEBUG_FORCE_SEND");
        return new Claim(schedule.getId(), requestId);
    }

    @Transactional(readOnly = true)
    public String generate(Claim claim) {
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
                PROACTIVE_SEED,
                schedule.getPendingContactReason(),
                state.name(),
                response.name(),
                characterSnapshotMapper.toSnapshot(relationship.getCharacter(), profile, relationship),
                relationshipSnapshotMapper.toSnapshot(relationship, status),
                history);

        AiChatResponse aiResponse = aiServerClient.proactive(request);
        return aiResponse.reply();
    }

    @Transactional
    public void complete(Claim claim, String reply, LocalDateTime now) {
        ProactiveContactSchedule schedule = scheduleRepository.findByIdForUpdate(claim.scheduleId()).orElseThrow();
        if (!claim.requestId().equals(schedule.getPendingRequestId())) return;

        Relationship relationship = schedule.getRelationship();
        ChatRoom room = chatRoomRepository.findByRelationshipId(relationship.getId()).orElseThrow();
        if (!messageRepository.existsByProactiveRequestId(claim.requestId())) {
            messageRepository.save(ChatMessage.builder()
                    .senderType(SenderType.AI)
                    .content(reply)
                    .messageType(MessageType.TEXT)
                    .read(false)
                    .deleted(false)
                    .chatRoom(room)
                    .proactiveRequestId(claim.requestId())
                    .build());
            room.touch(now);
        }

        CharacterAiProfile profile = profileRepository.findById(relationship.getCharacter().getId()).orElseThrow();
        ProactiveRelationshipState state = stateResolver.resolve(relationship.getEmotion());
        LocalDateTime next = policy.nextCandidate(now, profile.getAttachment(), state);
        PreferredContactTimePolicy.Result preferred =
                preferredTimePolicy.evaluate(relationship.getCharacter().getPreferTime(), next);
        schedule.complete(now, preferred.preferred() ? next : preferred.nextPreferredTime());
    }

    @Transactional
    public void fail(Claim claim, RuntimeException exception, LocalDateTime now) {
        ProactiveContactSchedule schedule = scheduleRepository.findByIdForUpdate(claim.scheduleId()).orElse(null);
        if (schedule != null && claim.requestId().equals(schedule.getPendingRequestId())) {
            schedule.retry(exception, now);
        }
    }

    public record Claim(Long scheduleId, String requestId) {
    }
}
