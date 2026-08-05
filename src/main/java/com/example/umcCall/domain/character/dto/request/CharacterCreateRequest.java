package com.example.umcCall.domain.character.dto.request;

import com.example.umcCall.domain.image.enums.Gender;
import com.example.umcCall.domain.character.enums.Job;
import com.example.umcCall.domain.character.enums.PreferTime;
import com.example.umcCall.domain.character.enums.SpeechStyle;
import com.example.umcCall.domain.relationship.enums.RelationshipStage;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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

    @Schema(example = "김", description = "성(2자 이내)")
    @NotBlank
    @Size(max = 2)
    private String lastName;

    @Schema(example = "유나", description = "이름(5자 이내)")
    @NotBlank
    @Size(max = 5)
    private String firstName;

    @Schema(example = "FEMALE", description = "성별: MALE(남성) 또는 FEMALE(여성)")
    @NotNull
    private Gender gender;

    @Schema(example = "25", description = "캐릭터의 만 나이, 0~99")
    @NotNull
    @Min(0)
    @Max(99)
    private Integer age;

    @Schema(example = "UNIVERSITY_STUDENT", description = "직업: UNIVERSITY_STUDENT(대학생), EMPLOYEE(직장인), OTHER(기타)")
    @NotNull
    private Job job;

    @Schema(example = "https://callfromai-images.s3.ap-northeast-2.amazonaws.com/female_2.png",
            description = "프로필 사진 URL. GET /preset-images로 조회한 프리셋 이미지 URL 중 하나여야 함")
    @Size(max = 2048)
    @Pattern(regexp = "https://.+", message = "이미지 URL은 https 형식이어야 합니다.")
    private String imageUrl;

    @Schema(example = "50", description = "연애 온도(매력도) - 낮을수록 안정형, 높을수록 집착형(0~100)")
    @NotNull
    @Min(0)
    @Max(100)
    private Integer spiceLevel;

    @Schema(example = "MORNING", description = "선호 통화 시간대: MORNING(오전), DAY(낮), LATE_EVENING(늦은 오후), ANYTIME(언제든)")
    @NotNull
    private PreferTime preferTime;

    @Schema(example = "ENFP", description = "MBTI 16가지 유형 중 하나")
    @Pattern(regexp = "(?i)[EI][NS][TF][JP]", message = "MBTI는 유효한 4자리 유형이어야 합니다.")
    private String mbti;

    @Schema(example = "CASUAL", description = "말투: CASUAL(반말), SEMI_FORMAL(반존대), FORMAL(존댓말)")
    @NotNull
    private SpeechStyle speechStyle;

    @Schema(example = "SOME", description = "관계 단계: SOME(썸), EARLY_DATING(연인초기), LONG_TERM(오래된 연인)")
    @NotNull
    private RelationshipStage relationshipStage;

    @ArraySchema(
            arraySchema = @Schema(
                    description = "매력 키워드 목록, 1~5개, 우선순위(priority)는 1부터 연속된 값이어야 함",
                    example = """
                        [
                          {
                            "trait": "HUMOROUS",
                            "priority": 1
                          },
                          {
                            "trait": "PLAYFUL",
                            "priority": 2
                          },
                          {
                            "trait": "GOOD_LISTENER",
                            "priority": 3
                          }
                        ]
                        """
            ),
            schema = @Schema(implementation = TraitRequest.class)
    )
    @NotEmpty
    @Size(min = 1, max = 5)
    private List<@Valid TraitRequest> traits;
}
