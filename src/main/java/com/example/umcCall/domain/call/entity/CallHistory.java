package com.example.umcCall.domain.call.entity;

import com.example.umcCall.domain.call.enums.CallSpeaker;
import com.example.umcCall.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 통화 한 턴의 전사(transcript) 한 줄. 발화가 일어난 순간마다 독립적으로 append되는 이벤트 로그다
 * (USER final 확정 시 · AI TTS 송신 성공 시). id(IDENTITY) 단조증가 + createdAt이 발화 순서/시각이 된다.
 */
@Entity
@Table(name="call_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CallHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="call_history_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name="speaker", nullable = false)
    private CallSpeaker speaker;

    @Column(name="content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 소속 통화. 저장 시 프록시(getReferenceById)로 FK만 연결하므로 SELECT를 유발하지 않게 LAZY. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="call_id", nullable = false)
    private Call call;

    @Builder
    private CallHistory(CallSpeaker speaker, String content, Call call) {
        this.speaker = speaker;
        this.content = content;
        this.call = call;
    }
}
