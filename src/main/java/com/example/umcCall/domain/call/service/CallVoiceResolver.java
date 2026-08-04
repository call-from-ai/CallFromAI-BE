package com.example.umcCall.domain.call.service;

import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.repository.CharacterRepository;
import com.example.umcCall.domain.image.entity.PresetImage;
import com.example.umcCall.domain.image.enums.Gender;
import com.example.umcCall.domain.image.enums.TTSVoice;
import com.example.umcCall.domain.image.repository.PresetImageRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * "이 캐릭터는 어떤 목소리로 말하는가"를 정한다. 캐릭터가 고른 <b>프리셋 이미지</b>에 목소리가 매달려 있어,
 * 사용자는 따로 고를 게 없고 외형과 목소리가 어긋나지 않는다.
 *
 * <p>⚠ <b>통화당 한 번만 부른다</b>(WS 연결 시). TTS는 문장 단위로 턴마다 N번 불리므로 그때마다 여기로 오면
 * 문장 하나에 DB 두 방이 나간다. 해석 결과는 세션 스코프({@code ActiveCall.voice})에 들고 있는다.
 *
 * <p>⚠ <b>여기서 나는 어떤 실패도 통화를 끊지 않는다.</b> 목소리를 못 정한 건 기본 목소리로 말하면 되는 일이지
 * 전화를 못 받을 일이 아니다(TTS 실패 정책과 동일하게 로그만 남긴다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallVoiceResolver {

    /**
     * <b>캐릭터를 못 읽었을 때만</b> 쓰는 최후 성별. 그 지점에선 캐릭터 성별조차 모르는 상태라
     * 어느 쪽을 골라도 근거가 없어 하나로 고정한다(도달하면 이미 데이터가 깨진 것 — 로그로 잡는다).
     *
     * <p>⚠ 캐릭터를 읽은 <b>뒤</b>의 실패에는 쓰지 않는다 — 그땐 진짜 성별을 알고 있는데 이걸 쓰면
     * 남성 캐릭터가 여성 목소리로 말한다(성별별 폴백을 둔 이유가 사라진다).
     */
    private static final Gender UNKNOWN_GENDER_FALLBACK = Gender.FEMALE;

    private final CharacterRepository characterRepository;
    private final PresetImageRepository presetImageRepository;

    /**
     * 캐릭터가 낼 목소리. 매핑이 없거나 조회가 실패하면 성별 기본값으로 떨어뜨린다.
     *
     * @return 항상 non-null — 호출부가 폴백을 또 짤 필요가 없다
     */
    public TTSVoice resolve(Long characterId) {
        // ⚠ try 밖에 둔다 — 캐릭터를 읽은 뒤(=성별을 아는 상태) 프리셋 조회가 터지면
        // catch도 그 성별로 폴백해야 한다. 안 그러면 남성 캐릭터가 여성 목소리로 말한다.
        Gender gender = UNKNOWN_GENDER_FALLBACK;
        try {
            Character character = characterRepository.findById(characterId).orElse(null);
            if (character == null) {
                log.warn("[Call] 캐릭터를 찾을 수 없어 기본 목소리 사용. characterId={}", characterId);
                return TTSVoice.defaultFor(gender);
            }

            gender = character.getGender();
            // ⚠ imageUrl은 nullable이다 — 이미지를 안 고른 캐릭터가 실제로 존재할 수 있다.
            if (character.getImageUrl() == null) {
                log.info("[Call] 캐릭터에 이미지가 없어 기본 목소리 사용. characterId={}, gender={}",
                        characterId, gender);
                return TTSVoice.defaultFor(gender);
            }

            Optional<PresetImage> preset =
                    presetImageRepository.findByGenderAndImageUrl(gender, character.getImageUrl());
            if (preset.isEmpty()) {
                // 프리셋 URL이 바뀌었거나 목록에서 빠진 이미지다. 조용히 넘어가면 전 캐릭터가 같은 목소리로
                // 도는데도 아무도 모르므로 warn으로 남긴다.
                log.warn("[Call] 프리셋 매칭 실패 → 기본 목소리 사용. characterId={}, gender={}, imageUrl={}",
                        characterId, gender, character.getImageUrl());
                return TTSVoice.defaultFor(gender);
            }

            TTSVoice voice = preset.get().getVoice();
            log.debug("[Call] 통화 음성 결정. characterId={}, voice={}, speaker={}",
                    characterId, voice, voice.speakerId());
            return voice;
        } catch (RuntimeException e) {
            log.error("[Call] 통화 음성 조회 실패 → 기본 목소리 사용(통화는 유지). characterId={}, gender={}",
                    characterId, gender, e);
            return TTSVoice.defaultFor(gender);
        }
    }
}
