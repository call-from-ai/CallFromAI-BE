package com.example.umcCall.domain.call.dto.response;

import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.enums.CallRecordingStatus;
import com.example.umcCall.domain.call.enums.CallSummaryStatus;
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
public record CallDetailResponse(
        String characterName,
        String aiSummary,
        CallSummaryStatus summaryStatus,
        LocalDateTime createdAt,
        String audioUrl,
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
