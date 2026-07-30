package com.example.umcCall.domain.notification.push.enums;

/**
 * FCM 푸시 종류. FE는 data.type으로 이 값을 읽어 알림 클릭 시 동작을 분기한다.
 * - CHAT: 채팅 배너(방으로 이동)
 * - CALL: 수락/거절 커스텀 UI
 * - NOTICE: 공지·지난 알림 배너
 */
public enum PushType {
    CHAT,
    CALL,
    NOTICE
}
