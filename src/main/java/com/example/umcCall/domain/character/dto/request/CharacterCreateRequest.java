package com.example.umcCall.domain.character.dto.request;

import com.example.umcCall.domain.image.enums.Gender;
import com.example.umcCall.domain.character.enums.Job;
import com.example.umcCall.domain.character.enums.PreferTime;
import com.example.umcCall.domain.character.enums.SpeechStyle;
import com.example.umcCall.domain.relationship.enums.RelationshipStage;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(example = "김")
    @NotBlank
    @Size(max = 5)
    private String lastName;

    @Schema(example = "유나")
    @NotBlank
    @Size(max = 5)
    private String firstName;

    @Schema(example = "FEMALE")
    @NotNull
    private Gender gender;

    @Schema(example = "25")
    @NotNull
    @Min(0)
    @Max(99)
    private Integer age;

    @Schema(example = "UNIVERSITY_STUDENT")
    @NotNull
    private Job job;

    @Schema(example = "https://callfromai-images.s3.ap-northeast-2.amazonaws.com/female_2.png")
    @Size(max = 2048)
    @Pattern(regexp = "https://.+", message = "이미지 URL은 https 형식이어야 합니다.")
    private String imageUrl;

    @Schema(example = "50")
    @NotNull
    @Min(0)
    @Max(100)
    private Integer spiceLevel;

    @Schema(example = "MORNING")
    @NotNull
    private PreferTime preferTime;

    @Schema(example = "ENFP")
    @Pattern(regexp = "(?i)[EI][NS][TF][JP]", message = "MBTI는 유효한 4자리 유형이어야 합니다.")
    private String mbti;

    @Schema(example = "CASUAL")
    @NotNull
    private SpeechStyle speechStyle;

    @Schema(example = "SOME")
    @NotNull
    private RelationshipStage relationshipStage;

    @NotEmpty
    @Size(min = 1, max = 5)
    private List<@Valid TraitRequest> traits;
}
