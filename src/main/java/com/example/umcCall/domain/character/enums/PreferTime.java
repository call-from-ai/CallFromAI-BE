package com.example.umcCall.domain.character.enums;


/**
 * 캐릭터가 선호하는 통화 시간대. DB와 JSON은 영문 code를 사용하고, label은 화면 표시용이다.
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

    public String getLabel() {
        return label;
    }

}
