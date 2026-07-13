package com.example.umcCall.domain.character.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 캐릭터 매력 키워드 (온보딩에서 선택하는 성격 특성).
 * 자바 내부/DB 저장은 영문 상수명 그대로 쓰고, JSON 요청/응답에서는 한글 label만 주고받는다.
 */
public enum Trait {
    HUMOROUS("유머러스한"),
    PLAYFUL("장난기 많은"),
    AFFECTIONATE("애교 많은"),
    JEALOUS("질투심 폭발"),
    TALKATIVE("수다쟁이"),
    DAD_JOKE_LOVER("아재개그 좋아하는"),
    HOMEBODY("집순이/집돌이"),
    TEASING("놀리는 걸 좋아하는"),
    POSSESSIVE("집착하는"),
    TSUNDERE("촌데레"),
    EXPRESSIVE("표현을 많이 하는"),
    PET_NAME_LOVER("애칭을 자주 쓰는"),
    EXCLUSIVE("독점욕이 있는"),
    QUIRKY("4차원 같은"),
    LAID_BACK("털털한"),
    OPENLY_JEALOUS("질투를 숨기지 않는"),
    SHY("부끄러움을 많이 타는"),
    SMOOTH_TALKER("능청스러운"),
    FREQUENT_CHECKER("연락을 자주 확인하는"),
    GOOD_LISTENER("고민을 잘 들어주는"),
    COMPLIMENTER("칭찬을 많이 하는");

    private final String label;

    Trait(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static Trait from(String label) {
        for (Trait trait : values()) {
            if (trait.label.equals(label)) {
                return trait;
            }
        }
        throw new IllegalArgumentException("존재하지 않는 매력 키워드입니다: " + label);
    }
}
