package com.example.umcCall.domain.notification.push.service;

import com.example.umcCall.domain.notification.event.ActivityNotificationCreatedEvent;
import com.example.umcCall.domain.notification.push.dto.PushMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 인앱 활동 알림(기념일·통화약속·부재중 등)이 저장된 뒤, 그 내용을 사용자 기기에 푸시로 보낸다.
 *커밋 후 수신이라 알림 저장이 롤백되면 푸시도 나가지 않는다
 * 발송 자체는 트랜잭션 밖에서 일어나므로 FCM 네트워크 호출이 DB 커넥션을 쥐지 않는다.
 * 푸시가 실패해도 이미 커밋된 알림 저장에는 영향을 주지 않는다(로깅만).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityNotificationPushNotifier {

    private final PushNotificationService pushNotificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivityNotificationCreated(ActivityNotificationCreatedEvent event) {
        try {
            pushNotificationService.send(event.memberId(),
                    PushMessage.notice(event.notificationId(), event.title(), event.content()));
        } catch (Exception e) {
            log.error("활동 알림 푸시 발송 실패. notificationId={}, memberId={}",
                    event.notificationId(), event.memberId(), e);
        }
    }
}
