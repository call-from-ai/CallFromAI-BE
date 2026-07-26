package com.example.umcCall.domain.proactive.service;

import com.example.umcCall.domain.proactive.enums.ProactiveRelationshipState;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class RelationshipStateResolver {

    public ProactiveRelationshipState resolve(String emotion) {
        if (emotion == null || emotion.isBlank()) return ProactiveRelationshipState.NORMAL;
        String normalized = emotion.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("REPAIR") || normalized.contains("화해")) {
            return ProactiveRelationshipState.REPAIRING;
        }
        if (normalized.contains("CONFLICT") || normalized.contains("ANGER")
                || normalized.contains("싸움") || normalized.contains("분노")) {
            return ProactiveRelationshipState.CONFLICT;
        }
        if (normalized.contains("UPSET") || normalized.contains("HURT")
                || normalized.contains("서운") || normalized.contains("상처")) {
            return ProactiveRelationshipState.UPSET;
        }
        return ProactiveRelationshipState.NORMAL;
    }
}
