package com.example.umcCall.domain.character.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 캐릭터가 선호하는 통화 시간대. 자바 내부/DB 저장은 영문 상수명, JSON 요청/응답은 한글만 주고받는다.
 */
public enum PreferTime {
    MORNING("오전 시간대"),
    DAY("낮 시간대"),
    LATE_EVENING("늦은 오후 시간대"),
    ANYTIME("언제든 좋아요");

    private final String label;

    PreferTime(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static PreferTime from(String label) {
        for (PreferTime preferTime : values()) {
            if (preferTime.label.equals(label)) {
                return preferTime;
            }
        }
        throw new IllegalArgumentException("존재하지 않는 통화 시간대입니다: " + label);
    }
}
