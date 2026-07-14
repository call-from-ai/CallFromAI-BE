package com.example.umcCall.domain.relationship.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 회원과 캐릭터의 관계 단계. 자바 내부/DB 저장은 영문 상수명, JSON 요청/응답은 한글만 주고받는다.
 */
public enum RelationshipStage {
    SOME("썸"),
    EARLY_DATING("연인초기"),
    LONG_TERM("오래된 연인");

    private final String label;

    RelationshipStage(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static RelationshipStage from(String label) {
        for (RelationshipStage stage : values()) {
            if (stage.label.equals(label)) {
                return stage;
            }
        }
        throw new IllegalArgumentException("존재하지 않는 관계 단계입니다: " + label);
    }
}
