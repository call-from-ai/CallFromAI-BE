package com.example.umcCall.domain.character.enums;


/**
 * 캐릭터 직업. DB와 JSON은 영문 code를 사용하고, label은 화면 표시용이다.
 */
public enum Job {
    UNIVERSITY_STUDENT("대학생"),
    EMPLOYEE("직장인"),
    OTHER("기타");

    private final String label;

    Job(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

}
