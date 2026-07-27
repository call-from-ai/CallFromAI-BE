package com.example.umcCall.domain.proactive.service;

import com.example.umcCall.domain.character.entity.CharacterAiProfile;
import com.example.umcCall.domain.character.repository.CharacterAiProfileRepository;
import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.repository.ChatRoomRepository;
import com.example.umcCall.domain.proactive.entity.ProactiveContactSchedule;
import com.example.umcCall.domain.proactive.enums.ProactiveRelationshipState;
import com.example.umcCall.domain.proactive.repository.ProactiveContactScheduleRepository;
import com.example.umcCall.domain.relationship.entity.Relationship;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProactiveScheduleCoordinator {

    private final ProactiveContactScheduleRepository scheduleRepository;
    private final CharacterAiProfileRepository profileRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ProactiveContactPolicy policy;
    private final PreferredContactTimePolicy preferredTimePolicy;
    private final RelationshipStateResolver stateResolver;

    @Transactional
    public void create(Relationship relationship) {
        if (scheduleRepository.findByRelationshipId(relationship.getId()).isPresent()) return;
        scheduleRepository.save(ProactiveContactSchedule.create(
                relationship, nextCandidate(relationship, LocalDateTime.now())));
    }

    @Transactional
    public void reschedule(Relationship relationship) {
        ProactiveContactSchedule schedule = scheduleRepository.findByRelationshipId(relationship.getId())
                .orElseGet(() -> ProactiveContactSchedule.create(relationship, null));
        // 살아 있는 캐릭터는 메인 여부와 관계없이 항상 선제 연락 스케줄을 유지한다.
        // 과거 정책에서 비메인 전환 시 disabled 된 기존 행도 여기서 복구한다.
        LocalDateTime nextCheckAt = nextCandidate(relationship, LocalDateTime.now());
        schedule.reschedule(nextCheckAt);
        schedule.enable(nextCheckAt);
        scheduleRepository.save(schedule);
    }

    @Transactional
    public void activate(Relationship relationship) {
        ProactiveContactSchedule schedule = scheduleRepository.findByRelationshipId(relationship.getId())
                .orElseGet(() -> ProactiveContactSchedule.create(relationship, null));
        schedule.enable(nextCandidate(relationship, LocalDateTime.now()));
        scheduleRepository.save(schedule);
    }

    @Transactional
    public void deactivate(Relationship relationship) {
        scheduleRepository.findByRelationshipId(relationship.getId())
                .ifPresent(ProactiveContactSchedule::disable);
    }

    @Transactional
    public void delete(Relationship relationship) {
        scheduleRepository.deleteByRelationshipId(relationship.getId());
    }

    /**
     * 사용자 메시지 저장 경로에서 호출한다. 아직 이 저장소에 사용자 메시지 쓰기 API가 없으므로
     * 향후 해당 API는 메시지 저장과 이 메서드를 같은 트랜잭션에서 실행해야 한다.
     */
    @Transactional
    public void onUserMessage(Long relationshipId, LocalDateTime messageAt) {
        scheduleRepository.findByRelationshipId(relationshipId).ifPresent(schedule -> {
            Relationship relationship = schedule.getRelationship();
            schedule.recordUserResponse(nextCandidate(relationship, messageAt));
        });
    }

    private LocalDateTime nextCandidate(Relationship relationship, LocalDateTime fallbackAnchor) {
        LocalDateTime anchor = chatRoomRepository.findByRelationshipId(relationship.getId())
                .map(ChatRoom::getLastMessageAt)
                .orElse(null);
        if (anchor == null || anchor.isBefore(fallbackAnchor)) anchor = fallbackAnchor;

        CharacterAiProfile profile = profileRepository.findById(relationship.getCharacter().getId())
                .orElse(null);
        Double attachment = profile == null ? null : profile.getAttachment();
        ProactiveRelationshipState state = stateResolver.resolve(relationship.getEmotion());
        LocalDateTime candidate = policy.nextCandidate(anchor, attachment, state);
        PreferredContactTimePolicy.Result preferred =
                preferredTimePolicy.evaluate(relationship.getCharacter().getPreferTime(), candidate);
        return preferred.preferred() ? candidate : preferred.nextPreferredTime();
    }
}
