package com.example.umcCall.domain.call.enums;

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
    DIALING
}
