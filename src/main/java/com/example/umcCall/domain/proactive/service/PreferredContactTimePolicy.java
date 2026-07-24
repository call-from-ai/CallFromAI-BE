package com.example.umcCall.domain.proactive.service;

import com.example.umcCall.domain.character.enums.PreferTime;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.springframework.stereotype.Component;

@Component
public class PreferredContactTimePolicy {

    static final LocalTime MORNING_START = LocalTime.of(6, 0);
    static final LocalTime DAY_START = LocalTime.NOON;
    static final LocalTime LATE_EVENING_START = LocalTime.of(18, 0);

    public Result evaluate(PreferTime preferTime, LocalDateTime now) {
        PreferTime effective = preferTime == null ? PreferTime.ANYTIME : preferTime;
        if (effective == PreferTime.ANYTIME) {
            return new Result(true, now);
        }

        LocalTime start = startOf(effective);
        LocalTime end = endOf(effective);
        LocalTime time = now.toLocalTime();
        boolean preferred = !time.isBefore(start)
                && (effective == PreferTime.LATE_EVENING || time.isBefore(end));
        if (preferred) {
            return new Result(true, now);
        }

        LocalDateTime next = time.isBefore(start)
                ? now.toLocalDate().atTime(start)
                : now.toLocalDate().plusDays(1).atTime(start);
        return new Result(false, next);
    }

    public LocalDateTime nextPreferredTime(PreferTime preferTime, LocalDateTime after) {
        Result current = evaluate(preferTime, after);
        if (!current.preferred()) return current.nextPreferredTime();

        PreferTime effective = preferTime == null ? PreferTime.ANYTIME : preferTime;
        if (effective == PreferTime.ANYTIME) return after.plusDays(1);
        return after.toLocalDate().plusDays(1).atTime(startOf(effective));
    }

    private LocalTime startOf(PreferTime preferTime) {
        return switch (preferTime) {
            case MORNING -> MORNING_START;
            case DAY -> DAY_START;
            case LATE_EVENING -> LATE_EVENING_START;
            case ANYTIME -> throw new IllegalArgumentException("ANYTIME has no start");
        };
    }

    private LocalTime endOf(PreferTime preferTime) {
        return switch (preferTime) {
            case MORNING -> DAY_START;
            case DAY -> LATE_EVENING_START;
            case LATE_EVENING -> LocalTime.MAX;
            case ANYTIME -> throw new IllegalArgumentException("ANYTIME has no end");
        };
    }

    public record Result(boolean preferred, LocalDateTime nextPreferredTime) {
    }
}
