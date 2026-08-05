package com.example.umcCall.domain.call.entity;

import com.example.umcCall.domain.call.enums.CallRecordingStatus;
import com.example.umcCall.domain.call.enums.CallSummaryStatus;
import com.example.umcCall.domain.call.enums.CallSender;
import com.example.umcCall.domain.call.enums.CallStatus;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name="calls")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Call extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="call_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name="sender",nullable = false)
    private CallSender sender;

    @Enumerated(EnumType.STRING)
    @Column(name="status",nullable = false)
    private CallStatus status;

    /**
     * 통화 한 건의 주제 라벨(한 문장). ⚠ 채팅의 관계 누적 요약과 다른 물건이다 —
     * 이건 "무엇을 이야기했는가"고 이름·성격·관계 분위기가 들어가지 않는다.
     */
    @Column(name="ai_summary", length = 150)
    private String aiSummary;

    /**
     * 요약 준비 상태. 생성이 통화 종료 후라 {@code aiSummary}만으로는
     * "준비 중"과 "요약 없음"을 구별할 수 없다({@code recordingStatus}와 같은 이유).
     */
    @Enumerated(EnumType.STRING)
    @Column(name="summary_status", nullable = false, length = 20)
    private CallSummaryStatus summaryStatus = CallSummaryStatus.NONE;

    @Column(name="audio_url")
    private String audioUrl;

    /**
     * 녹음(다시듣기) 준비 상태. 업로드가 통화 종료 후 비동기라 {@code audioUrl}만으로는
     * "준비 중"과 "녹음 없음"을 구별할 수 없다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name="recording_status", nullable = false, length = 20)
    private CallRecordingStatus recordingStatus = CallRecordingStatus.NONE;

    @Column(name="call_time")
    private Integer callTime;

    /**
     * 사용자가 착신을 받은 시각(AI 발신). 미접속 스위퍼(PENDING → CANCELED)의 기준 시계다.
     * <p>⚠ {@code createdAt}을 겸용하면 벨이 끝나갈 무렵 받은 사용자가 받자마자 취소된다.
     */
    @Column(name="accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name="started_at")
    private LocalDateTime startedAt;

    @Column(name="ended_at")
    private LocalDateTime endedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="relationship_id",nullable = false)
    private Relationship relationship;

    @Builder
    private Call(Relationship relationship, CallSender sender) {
        this.relationship = relationship;
        this.sender = sender;
        this.status = (sender == CallSender.USER) ?
                CallStatus.DIALING : CallStatus.RINGING;
    }

    /**
     * 사용자가 착신을 받음(AI 발신). RINGING → PENDING.
     * <p>IN_PROGRESS로 바로 가지 않는다 — 오디오는 WS가 열린 뒤 흐르고, PENDING이 "받았지만 연결 전"인
     * 통화를 부재중 판정에서 제외해 준다.
     */
    public void accept() {
        if (status != CallStatus.RINGING) {
            throw new IllegalStateException("착신 대기 중인 통화만 받을 수 있습니다. 현재 상태=" + status);
        }
        this.status = CallStatus.PENDING;
        this.acceptedAt = LocalDateTime.now();
    }

    /**
     * 통화 연결됨. 연결 대기 중(DIALING 또는 PENDING)에서만 IN_PROGRESS로 전이한다.
     * <p>RINGING은 제외 — wsTicket은 accept에서만 나오므로 accept 없이는 소켓을 열 수 없다.
     */
    public void connect() {
        if (status != CallStatus.DIALING && status != CallStatus.PENDING) {
            throw new IllegalStateException("연결 대기 중인 통화만 연결할 수 있습니다. 현재 상태=" + status);
        }
        this.status = CallStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
    }

    /**
     * 통화 정상 종료. 연결된 통화(IN_PROGRESS)에서만 유효하다.
     */
    public void complete() {
        if (status != CallStatus.IN_PROGRESS) {
            throw new IllegalStateException("연결된 통화만 완료할 수 있습니다. 현재 상태=" + status);
        }
        this.status = CallStatus.COMPLETED;
        this.endedAt = LocalDateTime.now();
        this.callTime = (int) Duration.between(startedAt, endedAt).getSeconds();
    }

    /**
     * 연결되지 못한 채 서버/AI 측 사유로 취소됨(스트림 개설 실패 등). → CANCELED.
     * <p>사용자 취소가 아니다 — 연결 전 사용자 취소는 취소 API가 없어 현재 도달 불가다.
     */
    public void cancel() {
        this.status = CallStatus.CANCELED;
    }

    /**
     * 벨을 울렸으나 사용자가 받지 않음(부재중). RINGING → MISSED.
     * <p>이미 받은 통화(PENDING)는 대상이 아니다 — 사용자 부재가 아니라 연결 실패다.
     */
    public void markMissed() {
        if (status != CallStatus.RINGING) {
            throw new IllegalStateException("착신 대기 중인 통화만 부재중 처리할 수 있습니다. 현재 상태=" + status);
        }
        this.status = CallStatus.MISSED;
    }

    /**
     * 녹음 업로드를 시작했다. → {@code PROCESSING}.
     * <p>통화가 끝난 뒤라 상태 검증이 없다 — 녹음은 통화 상태 머신과 독립이고,
     * 어떤 사유로 끝났든(정상·시간 상한) 그때까지 들린 소리는 남길 가치가 있다.
     */
    public void startRecordingUpload() {
        this.recordingStatus = CallRecordingStatus.PROCESSING;
    }

    /** 녹음이 올라갔다. → {@code READY} + 다시듣기 URL. */
    public void completeRecording(String audioUrl) {
        this.audioUrl = audioUrl;
        this.recordingStatus = CallRecordingStatus.READY;
    }

    /** 녹음·업로드가 실패했다. → {@code FAILED}. 통화·전사는 그대로다(fail-open). */
    public void failRecording() {
        this.recordingStatus = CallRecordingStatus.FAILED;
    }

    /** 요약 생성을 시작했다. → {@code PROCESSING}. (녹음과 같이 통화 상태 머신과 독립이다) */
    public void startSummary() {
        this.summaryStatus = CallSummaryStatus.PROCESSING;
    }

    /** 요약이 만들어졌다. → {@code READY} + 주제 라벨. */
    public void completeSummary(String aiSummary) {
        this.aiSummary = aiSummary;
        this.summaryStatus = CallSummaryStatus.READY;
    }

    /** 요약 생성이 실패했다. → {@code FAILED}. 통화·전사·녹음은 그대로다(fail-open). */
    public void failSummary() {
        this.summaryStatus = CallSummaryStatus.FAILED;
    }

    /**
     * 요약할 대화가 없다. → {@code NONE}.
     * <p>전사가 한 줄도 없는 통화(미연결·즉시 종료)다 — <b>LLM을 부르지 않는다</b>.
     * {@code FAILED}와 구별하는 이유는 프론트의 재시도 판단이 갈리기 때문이다(둘 다 재시도는 무의미하나
     * 문구가 다르다: "대화 없음" vs "요약 실패").
     */
    public void markSummaryUnavailable() {
        this.summaryStatus = CallSummaryStatus.NONE;
    }

    /** 사용자가 착신을 거절함(AI 발신). RINGING → REJECTED. */
    public void reject() {
        if (status != CallStatus.RINGING) {
            throw new IllegalStateException("착신 대기 중인 통화만 거절할 수 있습니다. 현재 상태=" + status);
        }
        this.status = CallStatus.REJECTED;
    }
}
