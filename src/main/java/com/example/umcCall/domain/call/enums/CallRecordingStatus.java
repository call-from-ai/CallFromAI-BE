package com.example.umcCall.domain.call.enums;

/**
 * 통화 녹음(다시듣기)의 준비 상태. 통화 상세({@code GET /calls/{callId}})로 나가고, 프론트는 이 값으로
 * 플레이어를 띄울지 안내 문구를 띄울지 고른다.
 *
 * <p><b>왜 {@code audioUrl} 하나로 안 되는가</b>: 업로드가 <b>통화 종료 후 비동기</b>라 통화 직후엔
 * URL이 아직 없다. "null = 녹음 없음"으로만 계약하면 프론트가 <b>준비 중인 녹음을 없는 것으로 오판</b>하고,
 * 사용자는 잠시 뒤 생길 다시듣기를 영영 못 본다.
 *
 * <p>⚠ 프론트가 "키 유무"와 "빈 값" 두 규칙을 갖지 않도록 <b>항상 내려간다</b>(값이 없으면 {@code NONE}).
 */
public enum CallRecordingStatus {
    /** 녹음이 없다 — 연결되지 못한 통화이거나 녹음 기능이 꺼져 있었다. {@code audioUrl}도 없다. */
    NONE,
    /** 녹음은 됐고 업로드가 진행 중이다. 잠시 뒤 다시 조회하면 {@code READY}가 된다. */
    PROCESSING,
    /** 다시듣기 가능. {@code audioUrl}이 함께 내려간다. */
    READY,
    /** 녹음·업로드가 실패했다. 통화·전사에는 영향이 없다(fail-open) — 오디오만 없다. */
    FAILED
}
