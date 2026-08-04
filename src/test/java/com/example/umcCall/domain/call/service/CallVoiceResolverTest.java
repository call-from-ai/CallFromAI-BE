package com.example.umcCall.domain.call.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.repository.CharacterRepository;
import com.example.umcCall.domain.image.entity.PresetImage;
import com.example.umcCall.domain.image.enums.Gender;
import com.example.umcCall.domain.image.enums.TTSVoice;
import com.example.umcCall.domain.image.repository.PresetImageRepository;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 캐릭터 → 목소리 해석. <b>어떤 실패도 통화를 끊지 않는다</b>는 게 이 클래스의 계약이라,
 * 폴백 경로가 전부 값을 돌려주는지 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class CallVoiceResolverTest {

    private static final Long CHARACTER_ID = 20L;
    private static final String PRESET_URL = "https://cdn.example.com/preset/f-01.png";

    @Mock private CharacterRepository characterRepository;
    @Mock private PresetImageRepository presetImageRepository;
    @InjectMocks private CallVoiceResolver resolver;

    @Test
    void 프리셋에_매핑된_목소리를_돌려준다() {
        givenCharacter(Gender.FEMALE, PRESET_URL);
        when(presetImageRepository.findByGenderAndImageUrl(Gender.FEMALE, PRESET_URL))
                .thenReturn(Optional.of(presetImage(TTSVoice.YEJI)));

        assertThat(resolver.resolve(CHARACTER_ID)).isEqualTo(TTSVoice.YEJI);
    }

    @Test
    void 이미지를_안_고른_캐릭터는_성별_기본값으로_말한다() {
        // ⚠ Character.imageUrl은 nullable이다 — 폴백이 없으면 여기서 통화가 터진다.
        givenCharacter(Gender.MALE, null);

        assertThat(resolver.resolve(CHARACTER_ID)).isEqualTo(TTSVoice.defaultFor(Gender.MALE));
        // 조회할 URL 자체가 없으니 DB를 치지 않는다.
        verify(presetImageRepository, never()).findByGenderAndImageUrl(any(), any());
    }

    @Test
    void 프리셋_매칭이_끊기면_성별_기본값으로_말한다() {
        // 프리셋 이미지의 S3 경로가 바뀌면 매칭이 에러 없이 조용히 끊긴다. 그래도 전화는 받아져야 한다.
        givenCharacter(Gender.MALE, PRESET_URL);
        when(presetImageRepository.findByGenderAndImageUrl(Gender.MALE, PRESET_URL))
                .thenReturn(Optional.empty());

        assertThat(resolver.resolve(CHARACTER_ID)).isEqualTo(TTSVoice.defaultFor(Gender.MALE));
    }

    @Test
    void 성별_기본값은_성별을_따라간다() {
        // 단일 기본값이면 남성 캐릭터가 여성 목소리로 말하는 사고가 난다.
        assertThat(TTSVoice.defaultFor(Gender.MALE).gender()).isEqualTo(Gender.MALE);
        assertThat(TTSVoice.defaultFor(Gender.FEMALE).gender()).isEqualTo(Gender.FEMALE);
    }

    @Test
    void 캐릭터가_없어도_통화는_기본_목소리로_이어진다() {
        when(characterRepository.findById(CHARACTER_ID)).thenReturn(Optional.empty());

        assertThat(resolver.resolve(CHARACTER_ID)).isNotNull();
    }

    @Test
    void 조회가_터져도_예외를_밖으로_던지지_않는다() {
        // 여기서 예외가 새면 afterConnectionEstablished가 통화를 끊는다 — 목소리 때문에 전화가 안 되면 안 된다.
        when(characterRepository.findById(CHARACTER_ID)).thenThrow(new RuntimeException("DB down"));

        assertThat(resolver.resolve(CHARACTER_ID)).isNotNull();
    }

    @Test
    void 성별을_아는_상태에서_터지면_그_성별의_기본값으로_말한다() {
        // 캐릭터는 읽었으니 성별을 아는데, 프리셋 조회만 터진 경우. 여기서 고정 폴백으로 떨어지면
        // 남성 캐릭터가 여성 목소리로 말한다 — 성별별 폴백을 둔 이유가 사라진다.
        givenCharacter(Gender.MALE, PRESET_URL);
        when(presetImageRepository.findByGenderAndImageUrl(Gender.MALE, PRESET_URL))
                .thenThrow(new RuntimeException("DB down"));

        assertThat(resolver.resolve(CHARACTER_ID)).isEqualTo(TTSVoice.defaultFor(Gender.MALE));
    }

    @Test
    void 모든_화자는_성별이_지정돼_있다() {
        for (TTSVoice voice : TTSVoice.values()) {
            assertThat(voice.gender()).as(voice.name()).isNotNull();
            assertThat(voice.speakerId()).as(voice.name()).isNotBlank();
        }
    }

    @Test
    void 화자ID는_중복되지_않는다() {
        // 같은 ID가 두 멤버에 붙으면 프리셋 매핑이 사실상 하나로 합쳐진다.
        assertThat(Arrays.stream(TTSVoice.values()).map(TTSVoice::speakerId).distinct().count())
                .isEqualTo(TTSVoice.values().length);
    }

    private void givenCharacter(Gender gender, String imageUrl) {
        Character character = Character.builder()
                .lastName("김").firstName("하늘")
                .gender(gender)
                .imageUrl(imageUrl)
                .build();
        when(characterRepository.findById(CHARACTER_ID)).thenReturn(Optional.of(character));
    }

    /**
     * PresetImage는 생성자가 protected고 빌더도 세터도 없다(프리셋은 DB에서만 만들어진다).
     * 그 제약을 풀자고 프로덕션 코드에 구멍을 내는 대신, 테스트에서만 리플렉션으로 심는다.
     */
    private static PresetImage presetImage(TTSVoice voice) {
        try {
            Constructor<PresetImage> constructor = PresetImage.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            PresetImage preset = constructor.newInstance();
            Field field = PresetImage.class.getDeclaredField("voice");
            field.setAccessible(true);
            field.set(preset, voice);
            return preset;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
