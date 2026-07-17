package com.example.umcCall.domain.character.dto.request;

import com.example.umcCall.domain.character.enums.Gender;
import com.example.umcCall.domain.character.enums.Job;
import com.example.umcCall.domain.character.enums.PreferTime;
import com.example.umcCall.domain.character.enums.SpeechStyle;
import com.example.umcCall.domain.relationship.enums.RelationshipStage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 캐릭터 생성 요청.
 */
@Getter
@NoArgsConstructor
public class CharacterCreateRequest {

    @NotBlank
    @Size(max = 5)
    private String lastName;

    @NotBlank
    @Size(max = 5)
    private String firstName;

    @NotNull
    private Gender gender;

    @NotNull
    @Min(0)
    @Max(99)
    private Integer age;

    @NotNull
    private Job job;

    @Size(max = 2048)
    @Pattern(regexp = "https://.+", message = "이미지 URL은 https 형식이어야 합니다.")
    private String imageUrl;

    @NotNull
    @Min(0)
    @Max(100)
    private Integer spiceLevel;

    @NotNull
    private PreferTime preferTime;

    @Pattern(regexp = "(?i)[EI][NS][TF][JP]", message = "MBTI는 유효한 4자리 유형이어야 합니다.")
    private String mbti;

    @NotNull
    private SpeechStyle speechStyle;

    @NotNull
    private RelationshipStage relationshipStage;

    @NotEmpty
    @Size(min = 1, max = 5)
    private List<@Valid TraitRequest> traits;
}
