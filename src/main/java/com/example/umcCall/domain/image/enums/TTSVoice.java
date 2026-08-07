package com.example.umcCall.domain.image.enums;

/**
 * 통화 AI 음성으로 쓰는 화자. 프리셋 이미지({@code PresetImage.voice})에 매달려,
 * 캐릭터가 고른 이미지가 곧 목소리를 정한다.
 *
 * <p><b>enum 이름과 외부 ID를 분리해 둔 이유</b>: 외부 ID는 벤더에 그대로 넘어가는 계약값이라 우리가 못 바꾼다.
 * DB에는 enum 이름이 저장되므로, TTS 엔진을 바꿔도 <b>여기 값만</b> 고치면 되고 저장된 데이터는 그대로다.
 * 2026-08-06 CLOVA Voice → Typecast 교체가 실제로 이 경로로 끝났다 — <b>DB 마이그레이션 0건</b>이었다.
 *
 * <p>⚠ <b>{@code speakerId}(CLOVA)는 지우지 않고 남겨 둔다.</b> 지금 통화가 쓰는 값은 {@code voiceId}뿐이지만,
 * Typecast 쪽에 사고가 나면 {@code CallAudioWebSocketHandler}가 {@code voiceId()} 대신 {@code speakerId()}를
 * 읽도록 <b>한 줄만</b> 되돌려 CLOVA로 복귀할 수 있다. 그 복귀 경로를 살려두는 것이 이 필드의 유일한 용도다.
 * (짝이 되는 {@code ClovaVoiceClient}·{@code ClovaVoiceProperties}도 주석 상태로 남아 있다.)
 *
 * <p>⚠ CLOVA 시절의 <b>"24000Hz 지원 화자만 넣는다"</b> 제약은 <b>사라졌다</b>. Typecast 표준 TTS는 화자와
 * 무관하게 44.1kHz 16-bit 모노 고정이라, 화자를 늘려도 통화마다 샘플레이트가 갈릴 일이 없다.
 * (⚠ 단 {@code speakerId} 쪽을 되살리면 그 제약도 함께 살아난다 — 지금 값들은 전부 24000Hz 화자다.)
 *
 * <p>⚠ <b>{@code gender}는 폴백 전용이다.</b> 정상 경로에서 성별 정합성은 프리셋 데이터를 넣을 때
 * 사람이 맞춘다(여성 이미지엔 여성 화자) — 코드가 막아주지 않는다.
 */
public enum TTSVoice {

    // ⚠ enum 이름은 CLOVA 시절 화자명이라 Typecast 목소리와 다르다(DB에 이 이름이 저장돼 못 바꾼다).
    //   실제 목소리는 줄 끝 주석을 볼 것.
    // 여성                        Typecast voiceId        CLOVA speakerId(롤백 전용)
    HEERA("tc_69f2e455ea79fd197aa0476f", "nheera", Gender.FEMALE),          // 서현
    ARA_PRO("tc_68785db8ba9cd7503f27d921", "vara", Gender.FEMALE),          // 고운
    MINYOUNG("tc_68537c9420b646f2176890ba", "nminyoung", Gender.FEMALE),    // 서진
    YUNA_PRO("tc_68413e12459cfdf27b481183", "vyuna", Gender.FEMALE),        // 라연
    GOEUN_PRO("tc_6837dec48fc46637a9272b88", "vgoeun", Gender.FEMALE),      // 소예
    SHASHA("tc_67c90ad544cf859417f2fc3a", "nshasha", Gender.FEMALE),        // 예진
    YOUNGMI("tc_6788847e9939d48aeb8642d2", "nyoungmi", Gender.FEMALE),      // 해랑
    SOHYUN("tc_6731b307df12333201d12b94", "nes_c_sohyun", Gender.FEMALE),   // 설화
    SUJIN("tc_65e96ab52564d1136ecb1d67", "nsujin", Gender.FEMALE),          // 유미
    YEJI("tc_66d91cac31a58a718f750a49", "nyeji", Gender.FEMALE),            // 서희
    EUNSEO("tc_63aaec0d34ca719d00798a97", "neunseo", Gender.FEMALE),        // 수빈

    // 남성
    MINSANG("tc_63aaec04428dd87af3757d72", "nminsang", Gender.MALE),        // 서준
    DONGHYUN_PRO("tc_68662745779b66ba84fc4d84", "vdonghyun", Gender.MALE),  // 세헌
    SANGDO("tc_682e8798603b4e9ed84074f5", "nsangdo", Gender.MALE),          // 형진
    DAESEONG_PRO("tc_662a05b17419f60500ac5630", "vdaeseong", Gender.MALE),  // 이준
    RAEWON("tc_64d5cd4c35618dd3797c20ed", "nraewon", Gender.MALE),          // 평화
    KITAE("tc_6731b2e0855f351b98d30c48", "nkitae", Gender.MALE),            // 건석
    KYUWON("tc_61f0859907085fc68561c9a1", "nkyuwon", Gender.MALE),          // 지훈
    SEONGHOON("tc_68f0727fd62a5934102f7ec0", "nseonghoon", Gender.MALE),    // 민욱
    SIYOON("tc_686dc43ebd6351e06ee64d74", "nsiyoon", Gender.MALE),          // 원우
    SINU("tc_678884b481dfbfa3e4075a18", "nsinu", Gender.MALE),              // 장운
    JIHUN("tc_68257f68bc6e3c161ab5078d", "njihun", Gender.MALE);            // 필재

    /**
     * Typecast에 그대로 넘기는 화자 ID({@code tc_} 접두사). 통화가 실제로 쓰는 값.
     * <p>⚠ 비면 합성이 404로 떨어져 그 턴이 통째로 버려진다({@code TTSVoiceTest.모든_화자에_voiceId가_있다}가 막는다).
     */
    private final String voiceId;
    /** ⚠ CLOVA Voice 화자 ID. <b>현재 쓰이지 않는다</b> — 위 javadoc의 롤백 경로 전용이다. */
    private final String speakerId;
    /** 이 목소리의 성별. 폴백({@link #defaultFor}) 판정에만 쓴다. */
    private final Gender gender;

    TTSVoice(String voiceId, String speakerId, Gender gender) {
        this.voiceId = voiceId;
        this.speakerId = speakerId;
        this.gender = gender;
    }

    public String voiceId() {
        return voiceId;
    }

    public String speakerId() {
        return speakerId;
    }

    public Gender gender() {
        return gender;
    }

    /**
     * 매핑을 찾지 못했을 때 쓸 기본 목소리. 캐릭터가 이미지를 안 골랐거나(=nullable) 프리셋 URL이 바뀌어
     * 매칭이 끊긴 경우다.
     *
     * <p>⚠ <b>성별을 받는 이유</b>: 단일 기본값을 두면 남성 캐릭터가 여성 목소리로 말하는 사고가 난다.
     * <p>⚠ 이 값은 <b>yml 설정으로 빼지 않는다</b>: 전역 설정값이 있으면 매핑이 빠졌을 때 엉뚱한 목소리가
     * 조용히 나가고, 화자는 설정이 아니라 호출부가 넘긴다는 구조가 무너진다.
     */
    public static TTSVoice defaultFor(Gender gender) {
        return gender == Gender.MALE ? SINU : MINYOUNG;
    }
}
