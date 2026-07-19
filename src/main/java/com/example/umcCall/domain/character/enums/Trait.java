package com.example.umcCall.domain.character.enums;


/**
 * 캐릭터 매력 키워드 (온보딩에서 선택하는 성격 특성).
 * DB와 JSON은 영문 code를 사용하고, label은 화면 표시용이다.
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

    public String getLabel() {
        return label;
    }

}
