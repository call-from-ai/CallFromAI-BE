package com.example.umcCall.domain.call.dto.response;

import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.enums.CallRecordingStatus;
import com.example.umcCall.domain.call.enums.CallSummaryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 통화 기록 상세 조회 응답. 통화 목록에서 한 건을 탭해 들어오는 화면용 메타데이터.
 * 전문(script)은 별도 엔드포인트({@code GET /calls/{callId}/script})라 여기엔 담지 않는다.
 *
 * <p>⚠ <b>{@code aiSummary}도 {@code summaryStatus}와 함께 읽어야 한다</b> — 녹음과 같은 이유다.
 * 요약 생성이 통화 종료 후라 {@code aiSummary}가 없다고 요약이 없는 게 아니다({@code PROCESSING}일 수 있다).
 * 전역 Jackson {@code non_null} 설정으로 <b>null이면 응답 키가 생략</b>된다.
 *
 * <p>⚠ <b>{@code audioUrl}은 {@code recordingStatus}와 함께 읽어야 한다.</b> 녹음 업로드가 통화 종료 후
 * 비동기라, {@code audioUrl}이 없다고 해서 녹음이 없는 게 아니다({@code PROCESSING}일 수 있다).
 * {@code audioUrl} null만 보고 "녹음 없음"으로 그리면 잠시 뒤 생길 다시듣기를 사용자가 못 본다.
 *
 * <p>표시 시각은 {@code startedAt}이 아니라 <b>{@code createdAt}</b>(통화 목록과 동일 규약) —
 * 미연결 통화도 항상 존재해 시각이 비지 않는다. 캐릭터 이름은 목록과 같이 {@code firstName}만.
 *
 * @param characterName   상대 캐릭터 이름(firstName만 — 목록·채팅과 동일 규약)
 * @param aiSummary       통화 주제 라벨(한 문장). {@code summaryStatus=READY}일 때만 있다
 * @param summaryStatus   요약 준비 상태. <b>항상 내려간다</b>(값이 없으면 {@code NONE})
 * @param createdAt       통화 발신 시각(표시용 — 미연결 통화도 항상 존재)
 * @param audioUrl        통화 녹음 URL. {@code recordingStatus=READY}일 때만 있다
 * @param recordingStatus 녹음 준비 상태. <b>항상 내려간다</b>(값이 없으면 {@code NONE})
 */
@Schema(description = """
        통화 기록 상세. 전문(script)은 별도 엔드포인트(GET /calls/{callId}/script)에 있다.
        aiSummary·audioUrl은 각각 summaryStatus·recordingStatus와 **짝지어** 읽어야 한다""")
public record CallDetailResponse(
        @Schema(description = "상대 캐릭터 이름(firstName만 — 목록·채팅과 동일 규약)", example = "유나")
        String characterName,

        @Schema(description = "통화 주제 라벨(한 문장, 20자 내외). summaryStatus=READY일 때만 있고, 없으면 응답에서 키가 생략된다",
                example = "오늘 하루와 퇴근 후 일상 이야기",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String aiSummary,

        @Schema(description = """
                요약 준비 상태. 값이 없으면 NONE으로 **항상 내려간다**.
                재조회가 의미 있는 건 PROCESSING뿐이다 — FAILED·NONE은 다시 물어도 값이 생기지 않는다.
                NONE(요약할 대화가 없음) / PROCESSING(생성 중) / READY(완료) / FAILED(생성 실패)""",
                example = "READY", allowableValues = {"NONE", "PROCESSING", "READY", "FAILED"})
        CallSummaryStatus summaryStatus,

        @Schema(description = "통화 발신 시각(표시용). — 연결되지 못한 통화에도 항상 존재한다",
                example = "2026-08-06T20:30:00")
        LocalDateTime createdAt,

        @Schema(description = """
                통화 녹음 다시듣기 URL. recordingStatus=READY일 때만 있고, 없으면 응답에서 키가 생략된다.
                조회할 때마다 새로 발급되는 **한시적 서명 URL(60분 유효)** 이라 저장해 두고 재사용하면 안 된다.
                재생 진행바 길이는 callTime이 아니라 오디오 파일 자체 길이를 기준으로 한다.""",
                example = "https://callfromai-images.s3.ap-northeast-2.amazonaws.com/call-recordings/…?X-Amz-Signature=…",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String audioUrl,

        @Schema(description = """
                녹음 준비 상태. 값이 없으면 NONE으로 **항상 내려간다**.
                NONE(녹음 없음) / PROCESSING(업로드 중) / READY(재생 가능) / FAILED(녹음·업로드 실패)""",
                example = "READY", allowableValues = {"NONE", "PROCESSING", "READY", "FAILED"})
        CallRecordingStatus recordingStatus
) {
    /**
     * 응답을 만든다. {@code audioUrl}은 저장된 객체 key가 아니라 <b>presigned URL</b>이라야 하므로
     * 호출부(서비스)가 발급해 넘긴다(녹음이 없으면 null).
     */
    public static CallDetailResponse of(Call call, String audioUrl) {
        return new CallDetailResponse(
                call.getRelationship().getCharacter().getFirstName(),
                call.getAiSummary(),
                call.getSummaryStatus(),
                call.getCreatedAt(),
                audioUrl,
                call.getRecordingStatus());
    }
}
