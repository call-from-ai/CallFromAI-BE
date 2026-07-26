package com.example.umcCall.domain.relationship.enums;


/**
 * 회원과 캐릭터의 관계 단계. DB와 JSON은 영문 code를 사용하고, label은 화면 표시용이다.
 */
public enum RelationshipStage {
    SOME("썸"),
    EARLY_DATING("연인초기"),
    LONG_TERM("오래된 연인");

    private final String label;

    RelationshipStage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

}
