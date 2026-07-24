package com.example.umcCall.domain.character.enums;


/**
 * 캐릭터 직업. DB와 JSON은 영문 code를 사용하고, label은 화면 표시용이다.
 */
public enum Job {
    UNIVERSITY_STUDENT("대학생"),
<<<<<<< HEAD
=======
    /**
     * 기존 DB 호환용. 신규 요청은 UNIVERSITY_STUDENT를 사용한다.
     */
    @Deprecated
    STUDENT("대학생"),
>>>>>>> 628b71259d5d058cbca979824f8cfaa2606da5a8
    EMPLOYED("직장인"),
    UNEMPLOYED("무직");

    private final String label;

    Job(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

}
