package com.example.umcCall.domain.image.enums;

/**
 * 통화 AI 음성으로 쓰는 CLOVA Voice 화자. 프리셋 이미지({@code PresetImage.voice})에 매달려,
 * 캐릭터가 고른 이미지가 곧 목소리를 정한다.
 *
 * <p><b>enum 이름과 {@code speakerId}를 분리해 둔 이유</b>: {@code speakerId}는 CLOVA에 그대로 넘어가는
 * 외부 계약값이라 우리가 못 바꾼다. DB에는 enum 이름이 저장되므로, TTS 엔진을 바꾸거나 화자 ID가 개편돼도
 * <b>여기 한 줄만</b> 고치면 되고 저장된 데이터는 그대로다.
 *
 * <p>⚠ <b>24000Hz를 지원하는 화자만 넣는다.</b> 다운스트림은 CLOVA가 준 wav를 변환 없이 그대로 흘리고
 * 프론트가 헤더로 스펙을 읽으므로, 16000 전용 화자({@code mijin}·{@code jinho})가 섞이면 통화마다
 * 샘플레이트가 달라져 오디오 포맷 계약이 깨진다.
 *
 * <p>⚠ <b>{@code gender}는 폴백 전용이다.</b> 정상 경로에서 성별 정합성은 프리셋 데이터를 넣을 때
 * 사람이 맞춘다(여성 이미지엔 여성 화자) — 코드가 막아주지 않는다.
 */
public enum TTSVoice {

    // 여성
    HEERA("nheera", Gender.FEMALE),
    ARA_PRO("vara", Gender.FEMALE),
    MINYOUNG("nminyoung", Gender.FEMALE),
    YUNA_PRO("vyuna", Gender.FEMALE),
    GOEUN_PRO("vgoeun", Gender.FEMALE),
    SHASHA("nshasha", Gender.FEMALE),
    YOUNGMI("nyoungmi", Gender.FEMALE),
    SOHYUN("nes_c_sohyun", Gender.FEMALE),
    SUJIN("nsujin", Gender.FEMALE),
    YEJI("nyeji", Gender.FEMALE),
    EUNSEO("neunseo", Gender.FEMALE),

    // 남성
    MINSANG("nminsang", Gender.MALE),
    DONGHYUN_PRO("vdonghyun", Gender.MALE),
    SANGDO("nsangdo", Gender.MALE),
    DAESEONG_PRO("vdaeseong", Gender.MALE),
    RAEWON("nraewon", Gender.MALE),
    KITAE("nkitae", Gender.MALE),
    KYUWON("nkyuwon", Gender.MALE),
    SEONGHOON("nseonghoon", Gender.MALE),
    SIYOON("nsiyoon", Gender.MALE),
    SINU("nsinu", Gender.MALE),
    JIHUN("njihun", Gender.MALE);

    /** CLOVA Voice에 그대로 넘기는 화자 ID. */
    private final String speakerId;
    /** 이 목소리의 성별. 폴백({@link #defaultFor}) 판정에만 쓴다. */
    private final Gender gender;

    TTSVoice(String speakerId, Gender gender) {
        this.speakerId = speakerId;
        this.gender = gender;
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
     * <p>⚠ 기본값을 <b>Pro가 아닌 화자</b>로 고른 건 24000Hz 지원이 확실한 쪽으로 떨어뜨리기 위해서다 —
     * 폴백은 검증 없이 조용히 타는 경로라 여기서 포맷 계약이 깨지면 알아채기 어렵다.
     * <p>⚠ 이 값은 <b>yml 설정으로 빼지 않는다</b>: 전역 설정값이 있으면 매핑이 빠졌을 때 엉뚱한 목소리가
     * 조용히 나가고, 화자는 설정이 아니라 호출부가 넘긴다는 구조가 무너진다.
     */
    public static TTSVoice defaultFor(Gender gender) {
        return gender == Gender.MALE ? SINU : MINYOUNG;
    }
}
