package com.example.umcCall.domain.character.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 캐릭터 말투. 자바 내부/DB 저장은 영문 상수명, JSON 요청/응답은 한글만 주고받는다.
 */
public enum SpeechStyle {
    CASUAL("반말"),
    SEMI_FORMAL("반존대"),
    FORMAL("존댓말");

    private final String label;

    SpeechStyle(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static SpeechStyle from(String label) {
        for (SpeechStyle speechStyle : values()) {
            if (speechStyle.label.equals(label)) {
                return speechStyle;
            }
        }
        throw new IllegalArgumentException("존재하지 않는 말투입니다: " + label);
    }
}
