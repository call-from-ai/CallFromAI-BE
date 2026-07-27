package com.example.umcCall.domain.proactive.service;

import com.example.umcCall.domain.character.enums.PreferTime;
import com.example.umcCall.domain.proactive.enums.AttachmentLevel;
import com.example.umcCall.domain.proactive.enums.ProactiveAction;
import com.example.umcCall.domain.proactive.enums.ProactiveRelationshipState;
import com.example.umcCall.domain.proactive.enums.RecentResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class ProactiveContactPolicy {

    private static final Duration MINIMUM_COOLDOWN = Duration.ofMinutes(30);
    private static final double CALL_PROBABILITY = 0.20;

    private final PreferredContactTimePolicy preferredTimePolicy;

    public ProactiveContactPolicy(PreferredContactTimePolicy preferredTimePolicy) {
        this.preferredTimePolicy = preferredTimePolicy;
    }

    public Decision decide(Context context) {
        return decide(
                context,
                ThreadLocalRandom.current().nextDouble(),
                ThreadLocalRandom.current().nextDouble());
    }

    Decision decide(Context context, double randomValue) {
        return decide(context, randomValue, randomValue);
    }

    Decision decide(Context context, double intervalRandomValue, double actionRandomValue) {
        if (!context.enabled()) return Decision.blocked("PROACTIVE_CONTACT_DISABLED");
        if (context.optedOut()) return Decision.blocked("EXPLICIT_OPT_OUT");
        if (context.doNotDisturb()) return Decision.defer("DO_NOT_DISTURB", context.now().plusHours(1));
        if (context.activeSession()) return Decision.defer("ACTIVE_SESSION", context.now().plusMinutes(10));
        if (context.dailyContactCount() >= context.dailyContactLimit()) {
            return Decision.defer("DAILY_LIMIT_REACHED", context.now().toLocalDate().plusDays(1).atStartOfDay());
        }
        if (context.pausedUntil() != null && context.now().isBefore(context.pausedUntil())) {
            return Decision.defer("PAUSED", context.pausedUntil());
        }
        if (context.lastContactAt() != null
                && context.now().isBefore(context.lastContactAt().plus(MINIMUM_COOLDOWN))) {
            return Decision.defer("MINIMUM_COOLDOWN", context.lastContactAt().plus(MINIMUM_COOLDOWN));
        }
        if (context.consecutiveNoResponseCount() >= 3) {
            return Decision.blocked("THREE_NO_RESPONSES");
        }

        PreferredContactTimePolicy.Result preferred =
                preferredTimePolicy.evaluate(context.preferTime(), context.now());
        if (!preferred.preferred()) {
            return Decision.defer("NOT_PREFERRED_TIME", preferred.nextPreferredTime());
        }
        if (context.consecutiveNoResponseCount() == 2) {
            return Decision.defer("TWO_NO_RESPONSES",
                    preferredTimePolicy.nextPreferredTime(context.preferTime(), context.now()));
        }

        LocalDateTime anchor = context.lastContactAt() == null ? context.now() : context.lastContactAt();
        Duration interval = contactInterval(context, intervalRandomValue);
        LocalDateTime dueAt = anchor.plus(interval);
        if (context.now().isBefore(dueAt)) {
            return Decision.defer("INTERVAL_NOT_PASSED", dueAt);
        }
        if (context.busyLikely() || context.agentBusy()) {
            return Decision.defer("BUSY", context.now().plusMinutes(30));
        }

        if (canCall(context) && actionRandomValue < CALL_PROBABILITY) {
            return Decision.send(ProactiveAction.CALL, "CALL_CONDITIONS_MET", "CALL_OFFER");
        }
        if (context.relationshipState() == ProactiveRelationshipState.CONFLICT) {
            return Decision.send(ProactiveAction.CHAT, "CONFLICT_REPAIR", "CONFLICT_REPAIR");
        }
        return Decision.send(ProactiveAction.CHAT, "CHAT_CONDITIONS_MET",
                context.relationshipState() == ProactiveRelationshipState.NORMAL
                        ? "NORMAL_CHECK_IN"
                        : context.relationshipState().name());
    }

    public LocalDateTime nextCandidate(LocalDateTime anchor, Double attachment,
                                       ProactiveRelationshipState state) {
        Context context = new Context(anchor, anchor, null, true, false, false, false,
                0, 10, 0, 3, PreferTime.ANYTIME, AttachmentLevel.from(attachment), state,
                RecentResponse.POSITIVE, 0, false, false, false, false, null);
        return anchor.plus(contactInterval(context, ThreadLocalRandom.current().nextDouble()));
    }

    private Duration contactInterval(Context context, double randomValue) {
        if (context.relationshipState() == ProactiveRelationshipState.CONFLICT) {
            Duration minimum = switch (context.attachmentLevel()) {
                case LOW -> Duration.ofHours(5);
                case NORMAL -> Duration.ofHours(4);
                case HIGH -> Duration.ofHours(3);
            };
            return randomBetween(minimum, minimum.plusHours(1), randomValue);
        }

        Duration base = switch (context.attachmentLevel()) {
            case LOW -> Duration.ofHours(3);
            case NORMAL -> Duration.ofHours(2);
            case HIGH -> Duration.ofMinutes(90);
        };
        Duration relationshipAdjustment = switch (context.relationshipState()) {
            case NORMAL -> Duration.ZERO;
            case UPSET, REPAIRING -> Duration.ofHours(1);
            case CONFLICT -> throw new IllegalStateException();
        };
        Duration responseAdjustment = switch (context.recentResponse()) {
            case POSITIVE -> Duration.ZERO;
            case AMBIGUOUS -> Duration.ofMinutes(30);
            case NO_RESPONSE -> Duration.ofMinutes(45L * Math.max(1, context.consecutiveNoResponseCount()));
        };
        return randomized(base.plus(relationshipAdjustment).plus(responseAdjustment), randomValue);
    }

    private boolean canCall(Context context) {
        return context.callAllowed()
                && context.dailyCallCount() < context.dailyCallLimit()
                && context.recentResponse() == RecentResponse.POSITIVE
                && context.relationshipState() == ProactiveRelationshipState.NORMAL
                && !context.repeatedMissedCalls();
    }

    private Duration randomized(Duration interval, double randomValue) {
        double bounded = Math.max(0.0, Math.min(1.0, randomValue));
        double multiplier = (5.0 / 6.0) + bounded * (5.0 / 12.0);
        return Duration.ofSeconds(Math.round(interval.toSeconds() * multiplier));
    }

    private Duration randomBetween(Duration minimum, Duration maximum, double randomValue) {
        double bounded = Math.max(0.0, Math.min(1.0, randomValue));
        return minimum.plusSeconds(Math.round(maximum.minus(minimum).toSeconds() * bounded));
    }

    public record Context(
            LocalDateTime now,
            LocalDateTime lastContactAt,
            LocalDateTime pausedUntil,
            boolean enabled,
            boolean optedOut,
            boolean doNotDisturb,
            boolean activeSession,
            int dailyContactCount,
            int dailyContactLimit,
            int dailyCallCount,
            int dailyCallLimit,
            PreferTime preferTime,
            AttachmentLevel attachmentLevel,
            ProactiveRelationshipState relationshipState,
            RecentResponse recentResponse,
            int consecutiveNoResponseCount,
            boolean busyLikely,
            boolean agentBusy,
            boolean callAllowed,
            boolean repeatedMissedCalls,
            LocalDateTime nextPreferredTime
    ) {
    }

    public record Decision(ProactiveAction action, String reason,
                           LocalDateTime nextCheckAt, String contactReason) {
        static Decision blocked(String reason) {
            return new Decision(ProactiveAction.BLOCKED, reason, null, null);
        }

        static Decision defer(String reason, LocalDateTime nextCheckAt) {
            return new Decision(ProactiveAction.DEFER, reason, nextCheckAt, null);
        }

        static Decision send(ProactiveAction action, String reason, String contactReason) {
            return new Decision(action, reason, null, contactReason);
        }
    }
}
