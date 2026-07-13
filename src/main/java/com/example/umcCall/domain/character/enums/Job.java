package com.example.umcCall.domain.character.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 캐릭터 직업. 자바 내부/DB 저장은 영문 상수명, JSON 요청/응답은 한글만 주고받는다.
 */
public enum Job {
    STUDENT("대학생"),
    EMPLOYED("직장인"),
    UNEMPLOYED("무직");

    private final String label;

    Job(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static Job from(String label) {
        for (Job job : values()) {
            if (job.label.equals(label)) {
                return job;
            }
        }
        throw new IllegalArgumentException("존재하지 않는 직업입니다: " + label);
    }
}
