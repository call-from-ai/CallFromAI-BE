package com.example.umcCall.domain.call.repository;

import com.example.umcCall.domain.call.dto.response.CallIncomingResponse;
import com.example.umcCall.domain.call.dto.response.CallListItem;
import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.enums.CallStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import com.example.umcCall.domain.call.enums.CallSender;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 통화 기록 저장/조회 담당. 전사(transcript) 저장·통화 이력 조회가 여기에 붙는다.
 */
public interface CallRepository extends JpaRepository<Call, Long> {

    /**
     * 회원의 통화 목록을 최신순(createdAt DESC)으로 조회한다. 상대 캐릭터 이름까지 한 번에 조인해
     * DTO로 프로젝션한다(N+1·lazy 회피). 개수 제한·상태 필터는 호출부가 준다.
     */
    @Query("""
            select new com.example.umcCall.domain.call.dto.response.CallListItem(
                c.id, ch.firstName, c.sender, c.aiSummary, c.summaryStatus, c.createdAt, c.status)
            from Call c
                join c.relationship r
                join r.character ch
            where r.memberId = :memberId
              and c.status in :statuses
            order by c.createdAt desc
            """)
    List<CallListItem> findRecentCallList(@Param("memberId") Long memberId,
                                          @Param("statuses") Collection<CallStatus> statuses,
                                          Pageable pageable);

    /**
     * 회원의 착신 대기 통화를 최신순으로 조회한다. 캐릭터까지 조인해 DTO로 프로젝션한다(N+1 회피).
     * <p>⚠ 반환형이 {@code List}인 이유: 호출부는 1건만 쓰지만 회원 기준 RINGING이 2건 생기는
     * 엣지(메인 캐릭터 교체)가 있어 {@code Optional} 반환은 예외로 터진다.
     */
    @Query("""
            select new com.example.umcCall.domain.call.dto.response.CallIncomingResponse(
                c.id, ch.id, ch.firstName, ch.imageUrl, c.createdAt)
            from Call c
                join c.relationship r
                join r.character ch
            where r.memberId = :memberId
              and c.status = :status
            order by c.createdAt desc
            """)
    List<CallIncomingResponse> findIncomingCalls(@Param("memberId") Long memberId,
                                                 @Param("status") CallStatus status,
                                                 Pageable pageable);

    /**
     * 벨만 울리다 만 통화 id를 오래된 것부터 조회한다(스위퍼용).
     * <p>기준 시각은 {@code createdAt} — AI 발신은 "Call 생성 = 벨 울림"이다.
     * ⚠ FCM 푸시가 붙으면 전달 시각과 어긋날 수 있어 재검토 대상.
     */
    @Query("""
            select c.id from Call c
            where c.status = :status
              and c.createdAt < :threshold
            order by c.createdAt, c.id
            """)
    List<Long> findTimedOutIds(@Param("status") CallStatus status,
                               @Param("threshold") LocalDateTime threshold,
                               Pageable pageable);

    /**
     * 받았지만 끝내 접속하지 않은 통화 id를 오래된 것부터 조회한다(스위퍼용).
     *
     * <p>⚠ 기준 시각이 {@code createdAt}이 아니라 <b>{@code acceptedAt}</b>인 것이 핵심이다. 벨
     * 타임아웃과 원점을 공유하면 유예가 "받기까지 걸린 시간"만큼 깎여, 벨이 끝나갈 무렵 받은 사용자는
     * 받자마자 CANCELED된다. 위 {@code findTimedOutIds}와 합치지 말 것.
     *
     * <p>{@code coalesce}는 {@code acceptedAt} 도입 이전 행(null) 폴백 — 빼면 그 행들이 영구히 안 걷혀
     * 해당 관계의 이후 예약이 전부 막힌다. {@code status}는 PENDING 고정이나 이 저장소 관례대로 바인딩한다.
     */
    @Query("""
            select c.id from Call c
            where c.status = :status
              and coalesce(c.acceptedAt, c.createdAt) < :threshold
            order by coalesce(c.acceptedAt, c.createdAt), c.id
            """)
    List<Long> findStalePendingIds(@Param("status") CallStatus status,
                                   @Param("threshold") LocalDateTime threshold,
                                   Pageable pageable);

    /**
     * 시간 상한을 넘겨 진행 중인 통화 id를 오래된 것부터 조회한다(스위퍼용).
     * <p>기준 시각은 {@code startedAt} — 상한은 "통화가 얼마나 오래 이어졌는가"라서 벨 대기 시간은 뺀다.
     * {@code IN_PROGRESS}는 {@code connect()}가 만든 상태라 {@code startedAt}이 항상 있다.
     */
    @Query("""
            select c.id from Call c
            where c.status = :status
              and c.startedAt < :threshold
            order by c.startedAt, c.id
            """)
    List<Long> findOverrunIds(@Param("status") CallStatus status,
                              @Param("threshold") LocalDateTime threshold,
                              Pageable pageable);

    /**
     * 상태 전이 대상 통화를 비관적 락으로 집는다. 스위퍼 마감과 사용자 accept/connect가 겹칠 때
     * 한쪽만 이기게 한다 — 호출부는 락 뒤 상태를 다시 확인해야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Call c where c.id = :id")
    Optional<Call> findByIdForUpdate(@Param("id") Long id);

    boolean existsByRelationshipIdAndStatusIn(Long relationshipId, Collection<CallStatus> statuses);

    /**
     * 관계에 속한 통화를 전부 지운다. 캐릭터 물리 삭제(하드 딜리트) 전용이다.
     * <p>⚠ 호출 전에 {@code CallHistoryRepository.deleteByRelationshipId}로 <b>전사를 먼저</b> 지워야 한다 —
     * {@code call_history.call_id}가 {@code nullable=false} FK인데 {@code Call}엔 cascade가 없다.
     */
    @Modifying
    @Query("delete from Call c where c.relationship.id = :relationshipId")
    void deleteByRelationshipId(@Param("relationshipId") Long relationshipId);

    /**
     * 관계의 활성 통화를 <b>비관적 락으로</b> 가져온다(발신 중복 방어용).
     * <p>{@code exists}가 아닌 이유: 발신 정책이 상태마다 달라서다 — 사용자 재시도로 남은 DIALING은
     * 취소하고 진행하지만, 그 외(RINGING·PENDING·IN_PROGRESS)는 거절한다.
     *
     * <p>락이 필요한 이유: 호출부가 읽은 통화를 {@code cancel()}로 갱신하는데 {@code Call}엔
     * {@code @Version}이 없어 조건 없는 UPDATE가 나간다. 락이 없으면 그 사이 {@code connect()}가 커밋한
     * IN_PROGRESS를 덮어써 <b>오디오는 흐르는데 DB는 CANCELED</b>인 통화가 된다.
     * <p>관계 락과 역할이 다르다 — 관계 락은 새 통화 INSERT(dial↔fire), 이 락은 기존 통화의 상태
     * 갱신(dial↔connect)을 막는다. {@code connect()}는 관계를 잠그지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from Call c
            where c.relationship.id = :relationshipId
              and c.status in :statuses
            """)
    List<Call> findActiveByRelationshipIdForUpdate(@Param("relationshipId") Long relationshipId,
                                                   @Param("statuses") Collection<CallStatus> statuses);

    long countByRelationshipIdAndSenderAndStatusInAndCreatedAtAfter(
            Long relationshipId,
            CallSender sender,
            Collection<CallStatus> statuses,
            LocalDateTime createdAt);

    /** 관계 요약 통계의 원본 데이터. 완료 시각 최신순으로 반환한다. */
    @Query("""
            select c.endedAt from Call c
            where c.relationship.id = :relationshipId
              and c.status = com.example.umcCall.domain.call.enums.CallStatus.COMPLETED
              and c.endedAt is not null
            order by c.endedAt desc
            """)
    List<LocalDateTime> findCompletedCallTimes(@Param("relationshipId") Long relationshipId);

    /**
     * 녹음 업로드 중({@code PROCESSING})을 전부 실패로 내린다. 기동 시 1회 —
     * 부르는 쪽({@code CallRecordingService.failStaleUploads})에 왜 그래도 되는지가 적혀 있다.
     *
     * @return 마감한 건수
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update Call c
            set c.recordingStatus = com.example.umcCall.domain.call.enums.CallRecordingStatus.FAILED
            where c.recordingStatus = com.example.umcCall.domain.call.enums.CallRecordingStatus.PROCESSING
            """)
    int failStaleRecordings();

    /**
     * 요약 생성 중({@code PROCESSING})을 전부 실패로 내린다. 기동 시 1회 —
     * 녹음({@link #failStaleRecordings})과 같은 이유다: 생성은 앱이 살아 있을 때만 도므로
     * <b>기동 시점의 {@code PROCESSING}은 정의상 전부 죽은 것</b>이고, 안 걷으면 영영 "준비 중"으로 남는다.
     *
     * @return 마감한 건수
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update Call c
            set c.summaryStatus = com.example.umcCall.domain.call.enums.CallSummaryStatus.FAILED
            where c.summaryStatus = com.example.umcCall.domain.call.enums.CallSummaryStatus.PROCESSING
            """)
    int failStaleSummaries();
}
