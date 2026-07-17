package com.example.umcCall.domain.character.enums;


/**
 * 캐릭터 말투. DB와 JSON은 영문 code를 사용하고, label은 화면 표시용이다.
 */
public enum SpeechStyle {
    CASUAL("반말"),
    SEMI_FORMAL("반존대"),
    FORMAL("존댓말");

    private final String label;

    SpeechStyle(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

}
