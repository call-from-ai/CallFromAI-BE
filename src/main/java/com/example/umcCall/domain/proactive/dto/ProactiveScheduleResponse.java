package com.example.umcCall.domain.proactive.dto;

import com.example.umcCall.domain.character.enums.PreferTime;
import com.example.umcCall.domain.proactive.entity.ProactiveContactSchedule;
import com.example.umcCall.domain.proactive.enums.AttachmentLevel;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProactiveScheduleResponse(
        Long scheduleId,
        Long relationshipId,
        Long characterId,
        String characterName,
        boolean enabled,
        PreferTime preferTime,
        Double attachment,
        AttachmentLevel attachmentLevel,
        String relationshipEmotion,
        LocalDateTime nextCheckAt,
        LocalDateTime lastProactiveContactAt,
        int consecutiveNoResponseCount,
        boolean awaitingUserResponse,
        int dailyContactCount,
        int dailyCallCount,
        LocalDate dailyCountDate,
        LocalDateTime pausedUntil,
        String pendingRequestId,
        String pendingAction,
        String pendingContactReason,
        int pendingAttempts,
        LocalDateTime pendingRetryAt,
        String lastError
) {
    public static ProactiveScheduleResponse of(ProactiveContactSchedule schedule, Double attachment) {
        var relationship = schedule.getRelationship();
        var character = relationship.getCharacter();
        return new ProactiveScheduleResponse(
                schedule.getId(),
                relationship.getId(),
                character.getId(),
                character.getName(),
                schedule.isEnabled(),
                character.getPreferTime(),
                attachment,
                AttachmentLevel.from(attachment),
                relationship.getEmotion(),
                schedule.getNextCheckAt(),
                schedule.getLastProactiveContactAt(),
                schedule.getConsecutiveNoResponseCount(),
                schedule.isAwaitingUserResponse(),
                schedule.getDailyContactCount(),
                schedule.getDailyCallCount(),
                schedule.getDailyCountDate(),
                schedule.getPausedUntil(),
                schedule.getPendingRequestId(),
                schedule.getPendingAction() == null ? null : schedule.getPendingAction().name(),
                schedule.getPendingContactReason(),
                schedule.getPendingAttempts(),
                schedule.getPendingRetryAt(),
                schedule.getLastError());
    }
}
