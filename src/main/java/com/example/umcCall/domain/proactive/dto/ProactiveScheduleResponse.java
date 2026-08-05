package com.example.umcCall.domain.proactive.dto;

import com.example.umcCall.domain.character.enums.PreferTime;
import com.example.umcCall.domain.proactive.entity.ProactiveContactSchedule;
import com.example.umcCall.domain.proactive.enums.AttachmentLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "캐릭터별 선제 연락 스케줄의 설정, 실행 시각, 미응답, 일일 한도 및 재시도 상태")
public record ProactiveScheduleResponse(
        @Schema(description = "선제 연락 스케줄 ID", example = "12")
        Long scheduleId,
        @Schema(description = "회원과 캐릭터 사이의 관계 ID", example = "7")
        Long relationshipId,
        @Schema(description = "스케줄 대상 캐릭터 ID", example = "3")
        Long characterId,
        @Schema(description = "스케줄 대상 캐릭터 이름", example = "김유나")
        String characterName,
        @Schema(description = "현재 메인 캐릭터 여부. 통화 스케줄링은 메인 캐릭터만 허용된다.", example = "true")
        boolean main,
        @Schema(description = "허용되는 선제 연락 방식. 메인은 CHAT_AND_CALL, 비메인은 CHAT_ONLY", example = "CHAT_AND_CALL", allowableValues = {"CHAT_AND_CALL", "CHAT_ONLY"})
        String contactMode,
        @Schema(description = "스케줄 활성화 여부. false이면 자동 실행 및 강제 실행 claim이 생성되지 않는다.", example = "true")
        boolean enabled,
        @Schema(description = "캐릭터의 선호 연락 시간대", example = "LATE_EVENING")
        PreferTime preferTime,
        @Schema(
                description = "AI 프로필의 애착도 원본 값. 0~10 범위이며 값이 없으면 null일 수 있다.",
                example = "7.5",
                minimum = "0",
                maximum = "10")
        Double attachment,
        @Schema(description = "애착도를 정책 구간으로 변환한 값", example = "HIGH", allowableValues = {"LOW", "NORMAL", "HIGH"})
        AttachmentLevel attachmentLevel,
        @Schema(description = "현재 관계 감정. 스케줄 간격과 채팅·통화 선택 정책에 사용된다.", example = "NORMAL")
        String relationshipEmotion,
        @Schema(description = "다음 정책 평가 예정 시각. force-due 호출 시 현재 시각으로 변경된다.", example = "2026-08-05T20:30:00")
        LocalDateTime nextCheckAt,
        @Schema(description = "마지막으로 완료된 선제 채팅 또는 통화 시각. 아직 없으면 null이다.", example = "2026-08-05T18:10:00")
        LocalDateTime lastProactiveContactAt,
        @Schema(description = "선제 연락 이후 사용자가 연속으로 응답하지 않은 횟수", example = "0")
        int consecutiveNoResponseCount,
        @Schema(description = "최근 선제 연락 후 사용자 응답을 기다리는 상태인지 여부", example = "false")
        boolean awaitingUserResponse,
        @Schema(description = "dailyCountDate 기준 당일 완료된 전체 선제 연락 횟수", example = "2")
        int dailyContactCount,
        @Schema(description = "dailyCountDate 기준 당일 완료된 선제 통화 횟수", example = "1")
        int dailyCallCount,
        @Schema(description = "일일 연락 횟수가 집계된 날짜", example = "2026-08-05")
        LocalDate dailyCountDate,
        @Schema(description = "이 시각까지 선제 연락이 일시정지된 상태. 정지되지 않았으면 null이다.", example = "2026-08-05T22:00:00")
        LocalDateTime pausedUntil,
        @Schema(description = "현재 claim 또는 재시도 중인 요청 ID. pending 작업이 없으면 null이다.", example = "proactive-550e8400-e29b-41d4-a716-446655440000")
        String pendingRequestId,
        @Schema(description = "pending 작업 종류. pending 작업이 없으면 null이다.", example = "CALL", allowableValues = {"CHAT", "CALL"})
        String pendingAction,
        @Schema(description = "pending 연락을 선택한 내부 사유", example = "CALL_OFFER")
        String pendingContactReason,
        @Schema(description = "현재 pending 요청의 실패 후 재시도 횟수", example = "0")
        int pendingAttempts,
        @Schema(description = "실패한 pending 요청의 다음 재시도 시각. 재시도 대기가 아니면 null이다.", example = "2026-08-05T20:35:00")
        LocalDateTime pendingRetryAt,
        @Schema(description = "최근 처리 실패 메시지. 실패 이력이 없으면 null이다.", example = "AI server timeout")
        String lastError
) {
    public static ProactiveScheduleResponse of(ProactiveContactSchedule schedule, Double attachment) {
        var relationship = schedule.getRelationship();
        var character = relationship.getCharacter();
        return new ProactiveScheduleResponse(
                schedule.getId(),
                relationship.getId(),
                character.getId(),
                character.getFullName(),
                relationship.isMain(),
                relationship.isMain() ? "CHAT_AND_CALL" : "CHAT_ONLY",
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
