package com.example.umcCall.domain.call.enums;

/**
 * 통화 요약({@code aiSummary})의 준비 상태. 통화 목록·상세로 나가고, 프론트는 이 값으로
 * 요약을 띄울지 자리를 비워둘지 고른다.
 *
 * <p><b>왜 {@code aiSummary} 하나로 안 되는가</b>: {@link CallRecordingStatus}와 같은 이유다.
 * 요약 생성이 <b>통화 종료 후</b>라 통화 직후엔 값이 아직 없는데, "null = 요약 없음"으로만 계약하면
 * 프론트가 <b>준비 중인 요약을 없는 것으로 오판</b>한다.
 *
 * <p>⚠ <b>재시도 판단의 근거이기도 하다.</b> {@code PROCESSING}일 때만 재조회가 의미 있고,
 * {@code FAILED}·{@code NONE}은 몇 번을 다시 물어도 값이 생기지 않는다 — 프론트가 재시도 횟수를
 * 감으로 정하지 않아도 된다.
 *
 * <p>⚠ 프론트가 "키 유무"와 "빈 값" 두 규칙을 갖지 않도록 <b>항상 내려간다</b>(값이 없으면 {@code NONE}).
 */
public enum CallSummaryStatus {
    /** 요약할 대화가 없다 — 연결되지 못했거나 전사가 한 줄도 없는 통화. {@code aiSummary}도 없다. */
    NONE,
    /** 생성 중이다. 잠시 뒤 다시 조회하면 {@code READY}가 된다 — <b>재시도가 의미 있는 유일한 상태</b>. */
    PROCESSING,
    /** 요약 준비 완료. {@code aiSummary}가 함께 내려간다. */
    READY,
    /** 생성이 실패했다. 통화·전사·녹음에는 영향이 없다(fail-open) — 요약만 없다. 재시도해도 안 생긴다. */
    FAILED
}
