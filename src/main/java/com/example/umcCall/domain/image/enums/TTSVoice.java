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

    // 여성                Typecast voiceId   CLOVA speakerId(롤백 전용)
    HEERA("", "nheera", Gender.FEMALE),
    ARA_PRO("", "vara", Gender.FEMALE),
    MINYOUNG("", "nminyoung", Gender.FEMALE),
    YUNA_PRO("", "vyuna", Gender.FEMALE),
    GOEUN_PRO("", "vgoeun", Gender.FEMALE),
    SHASHA("", "nshasha", Gender.FEMALE),
    YOUNGMI("", "nyoungmi", Gender.FEMALE),
    SOHYUN("", "nes_c_sohyun", Gender.FEMALE),
    SUJIN("", "nsujin", Gender.FEMALE),
    YEJI("", "nyeji", Gender.FEMALE),
    EUNSEO("", "neunseo", Gender.FEMALE),

    // 남성
    MINSANG("", "nminsang", Gender.MALE),
    DONGHYUN_PRO("", "vdonghyun", Gender.MALE),
    SANGDO("", "nsangdo", Gender.MALE),
    DAESEONG_PRO("", "vdaeseong", Gender.MALE),
    RAEWON("", "nraewon", Gender.MALE),
    KITAE("", "nkitae", Gender.MALE),
    KYUWON("", "nkyuwon", Gender.MALE),
    SEONGHOON("", "nseonghoon", Gender.MALE),
    SIYOON("", "nsiyoon", Gender.MALE),
    SINU("", "nsinu", Gender.MALE),
    JIHUN("", "njihun", Gender.MALE);

    /**
     * Typecast에 그대로 넘기는 화자 ID({@code tc_} 접두사). 통화가 실제로 쓰는 값.
     * <p>⚠ <b>TODO: 아직 비어 있다.</b> Typecast {@code GET /v2/voices}로 성별당 11종을 골라 채워야 한다 —
     * 비어 있으면 합성이 404로 떨어져 그 턴이 통째로 버려진다. 배포 전 반드시 채울 것.
     * ({@code TTSVoiceTest.모든_화자에_voiceId가_있다}가 빈 값을 막는다)
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
