package com.example.umcCall.domain.call.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum CallStatus {
    COMPLETED,
    MISSED,
    REJECTED,
    CANCELED,
    IN_PROGRESS,
    /** AI 발신을 사용자가 아직 받지 않은 상태(벨 울림). 부재중(MISSED) 판정 대상은 이 상태뿐이다. */
    RINGING,
    /** 착신을 받았지만(accept) 아직 WebSocket을 열지 않은 상태. 받은 사용자가 부재중으로 오판되지 않게 구분한다. */
    PENDING,
    DIALING;

    /**
     * 아직 끝나지 않은 통화 = "이 관계는 통화 중". 발신 중복 방어(dial·fire)와 proactive 억제가 공유한다.
     * <p>한 곳에 모아 둔 이유: 사본이 갈라지면 한쪽만 아는 상태가 생겨 <b>중복 통화가 조용히 새어 나간다</b>.
     * 소켓이 없는 셋(DIALING·RINGING·PENDING)이 포함되므로 스위퍼가 이들을 유계로 유지하는 게 전제다.
     */
    public static final Set<CallStatus> ACTIVE =
            Collections.unmodifiableSet(EnumSet.of(DIALING, RINGING, PENDING, IN_PROGRESS));
}
