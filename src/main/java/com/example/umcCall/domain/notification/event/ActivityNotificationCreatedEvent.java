package com.example.umcCall.domain.notification.event;

/**
 * 인앱 활동 알림(ActivityNotification)이 저장됐다는 사실 통지. 알림 도메인이 푸시 발송의 근거로 쓴다.
 *
 * <p>⚠ 수신은 {@code @TransactionalEventListener(AFTER_COMMIT)}로 할 것 — 알림 저장이 롤백되면
 * DB엔 없는 알림으로 푸시만 나가고, notificationId로 개별 읽음 처리도 깨진다.
 *
 * @param memberId       푸시를 받을 회원 ID
 * @param notificationId 저장된 알림 ID(개별 읽음 처리를 위해 FCM data에 실린다)
 * @param title          알림 제목(배너)
 * @param content        알림 내용(배너)
 */
public record ActivityNotificationCreatedEvent(
        Long memberId,
        Long notificationId,
        String title,
        String content
) {
}
