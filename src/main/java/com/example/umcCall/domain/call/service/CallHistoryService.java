package com.example.umcCall.domain.call.service;

import com.example.umcCall.domain.call.dto.response.CallDetailResponse;
import com.example.umcCall.domain.call.dto.response.CallListResponse;
import com.example.umcCall.domain.call.dto.response.CallScriptResponse;
import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.entity.CallHistory;
import com.example.umcCall.domain.call.enums.CallSpeaker;
import com.example.umcCall.domain.call.enums.CallStatus;
import com.example.umcCall.domain.call.exception.CallErrorCode;
import com.example.umcCall.domain.call.exception.CallException;
import com.example.umcCall.domain.call.repository.CallHistoryRepository;
import com.example.umcCall.domain.call.repository.CallRepository;
import com.example.umcCall.global.infra.s3.S3Uploader;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 통화 전사(transcript) 저장·조회 담당. 저장은 통화 중 워커가, 조회는 통화 종료 후 클라이언트가 부른다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CallHistoryService {

    private final CallRepository callRepository;
    private final CallHistoryRepository callHistoryRepository;
    private final CallArtifactRegistry callArtifactRegistry;
    private final TransactionTemplate transactionTemplate;
    private final S3Uploader s3Uploader;

    /** 목록에 노출할 통화 상태 = 종료된 것만. 진행 중(DIALING/RINGING/IN_PROGRESS)은 아직 기록이 아니라 제외한다. */
    private static final Set<CallStatus> TERMINAL_STATUSES = EnumSet.of(
            CallStatus.COMPLETED, CallStatus.CANCELED, CallStatus.MISSED, CallStatus.REJECTED);

    /** 최근 통화 노출 개수. 프론트와 20건 고정 합의(페이지네이션 없음). */
    private static final int RECENT_CALL_LIMIT = 20;

    /**
     * 통화 전사 한 줄을 저장한다. 발화가 일어난 순간마다(USER final · AI TTS 송신 성공) 호출되는 append-only 저장.
     * <p>callId로 Call 프록시({@code getReferenceById})만 얻어 FK를 채우므로 턴마다 Call을 재조회하지 않는다.
     * <p>이 메서드의 트랜잭션은 짧게 끝난다 — {@code chat()}의 느린 REST는 이 트랜잭션 밖(호출부 워커)에서 돈다.
     */
    public void appendHistory(Long callId, CallSpeaker speaker, String content) {
        callHistoryRepository.save(
                CallHistory.builder()
                        .call(callRepository.getReferenceById(callId))
                        .speaker(speaker)
                        .content(content)
                        .build());
    }

    /**
     * 통화 전사(script) 전문을 조회한다. 발화 순서(id ASC)대로 전체를 반환한다(페이지네이션 없음).
     * <p>검증: 통화 존재({@code CALL_NOT_FOUND}) → 본인 소유({@code CALL_ACCESS_DENIED}) →
     * 완료 상태({@code CALL_NOT_COMPLETED}). <b>완료된 통화만 전문을 준다</b> — 진행 중/취소/거절/부재중은 막는다
     * (전문은 종료된 통화의 완결된 기록이라는 계약). 존재·소유·완료를 백엔드가 구분해 내려주고, 화면 분기는 프론트가 정한다.
     */
    @Transactional(readOnly = true)
    public CallScriptResponse getScript(Long memberId, Long callId) {
        loadCompletedOwnedCall(memberId, callId);
        return CallScriptResponse.of(callId, callHistoryRepository.findByCallIdOrderByIdAsc(callId));
    }

    /**
     * 통화 기록 상세(요약/시작시각/오디오)를 조회한다. 통화 목록에서 탭해 들어오는 화면용 메타데이터.
     * <p>검증 계단은 {@link #getScript}와 동일하다 — 존재({@code CALL_NOT_FOUND}) → 본인 소유
     * ({@code CALL_ACCESS_DENIED}) → 완료({@code CALL_NOT_COMPLETED}). 상세도 <b>완료된 통화의
     * 완결된 기록</b>이라는 계약을 전문과 공유한다. {@code aiSummary}·{@code audioUrl}은 각각
     * {@code summaryStatus}·{@code recordingStatus}와 짝으로 읽어야 한다(둘 다 종료 후 비동기 생성).
     *
     * <p><b>{@code wait}=true면 산출물이 준비될 때까지 상한까지 기다렸다가 응답한다</b> — 프론트가
     * 종료 화면에서 폴링 없이 <b>조회 한 번</b>으로 요약·녹음을 받게 하려는 것이다. 기다리는 주체가
     * 통화 소켓이 아니라 <b>이 HTTP 요청</b>이라, 통화가 어떤 경로로 끝났든(끊기 버튼 · 시간 상한 ·
     * 소켓 끊김) 똑같이 동작한다.
     *
     * <p>⚠ <b>대기는 트랜잭션 밖에서 한다</b>({@code NOT_SUPPORTED} + {@link TransactionTemplate}).
     * 트랜잭션 안에서 기다리면 대기 내내 DB 커넥션을 잡아, 동시에 끝난 통화 몇 건만 겹쳐도 풀이 마른다.
     *
     * <p>⚠ 검증(존재·소유·완료)을 <b>기다리기 전에</b> 먼저 한다 — 남의 통화 ID로 요청 스레드를
     * 붙잡을 수 없게 하려는 것이자, 기존 "소유를 상태보다 먼저 본다"는 계단과 같은 순서다.
     *
     * <p>⚠ 상한을 넘겨도 실패가 아니라 <b>그 시점의 상태</b>({@code PROCESSING})로 응답한다. 산출물은
     * 백그라운드에서 계속 만들어지므로 프론트는 그 상태를 보고 다시 조회하면 된다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CallDetailResponse getCallDetail(Long memberId, Long callId, boolean wait) {
        CallDetailResponse detail = readCallDetail(memberId, callId);
        if (!wait || !callArtifactRegistry.await(callId)) {
            // 기다릴 대상이 없었으면(이미 끝났거나 산출물이 없는 통화) 다시 읽어봐야 같은 값이다.
            return detail;
        }
        return readCallDetail(memberId, callId);
    }

    /**
     * 상세 조회 본체. <b>짧은 트랜잭션 하나</b>로 검증·조회·DTO 변환까지 끝낸다 —
     * 지연 로딩(관계→회원)이 트랜잭션 안에서 끝나야 하고, 대기 구간과는 분리돼야 한다.
     */
    private CallDetailResponse readCallDetail(Long memberId, Long callId) {
        return transactionTemplate.execute(status -> {
            Call call = loadCompletedOwnedCall(memberId, callId);
            // 저장된 녹음 key를 유저가 재생할 수 있는 한시적 presigned URL로 바꾼다(녹음 없으면 null).
            String audioUrl = call.getAudioUrl() == null ? null : s3Uploader.presignedGetUrl(call.getAudioUrl());
            return CallDetailResponse.of(call, audioUrl);
        });
    }

    /**
     * callId로 Call을 로드하고 <b>존재 → 본인 소유 → 완료</b> 3단 검증을 통과시킨다.
     * 전문·상세 조회가 공유하는 검증 계단(완료된 통화의 기록만 조회 가능하다는 계약).
     */
    private Call loadCompletedOwnedCall(Long memberId, Long callId) {
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CallException(CallErrorCode.CALL_NOT_FOUND));
        if (!call.getRelationship().getMemberId().equals(memberId)) {
            throw new CallException(CallErrorCode.CALL_ACCESS_DENIED);
        }
        if (call.getStatus() != CallStatus.COMPLETED) {
            throw new CallException(CallErrorCode.CALL_NOT_COMPLETED);
        }
        return call;
    }

    /**
     * 내 통화 목록을 조회한다. <b>종료된 통화(COMPLETED/CANCELED/MISSED/REJECTED)만 최신순 최대 20건</b>.
     * <p>진행 중인 통화는 아직 기록이 아니라 제외한다. 페이지네이션 없음(20건 고정 합의).
     * 캐릭터 이름까지 리포지토리가 조인·프로젝션해 N+1 없이 한 방에 가져온다.
     */
    @Transactional(readOnly = true)
    public CallListResponse getCallList(Long memberId) {
        return CallListResponse.of(callRepository.findRecentCallList(
                memberId, TERMINAL_STATUSES, PageRequest.of(0, RECENT_CALL_LIMIT)));
    }
}
