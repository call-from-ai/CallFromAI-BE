package com.example.umcCall.domain.notification.push.service;

import com.example.umcCall.domain.call.event.CallRingingEvent;
import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.repository.ChatRoomRepository;
import com.example.umcCall.domain.notification.push.dto.PushMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AI 착신({@code RINGING} 통화 생성)을 사용자 기기에 푸시로 알린다.
 *
 * <p>통화 도메인이 아니라 알림 도메인에 두는 이유: 착신 푸시 payload에 필요한 {@code chatRoomId}가
 * <b>채팅 도메인 값</b>이라, 통화 쪽에 두면 통화가 채팅 저장소를 직접 참조하게 된다(#112에서 걷어낸
 * 의존이다). 통화는 {@link CallRingingEvent}만 던지고 조립은 여기서 한다.
 *
 * <p>커밋 후(AFTER_COMMIT) 수신이라 롤백된 통화로 벨이 울리는 일이 없다.
 *
 * <p>⚠ 푸시가 전달되지 않아도 통화 상태는 건드리지 않는다 — 사유는 {@link CallRingingEvent} 참고.
 * 방해금지·심야 통화로 <b>발신 자체를</b> 막는 건 proactive 스케줄링(#109)의 몫이고, 여기서 중복
 * 판정하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallPushNotifier {

    private final ChatRoomRepository chatRoomRepository;
    private final PushNotificationService pushNotificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCallRinging(CallRingingEvent event) {
        try {
            pushNotificationService.send(event.memberId(), PushMessage.call(
                    event.callId(),
                    event.characterId(),
                    event.characterName(),
                    event.characterImageUrl(),
                    resolveChatRoomId(event.relationshipId())));
        } catch (Exception e) {
            // 푸시 실패가 이미 커밋된 통화 생성에 영향을 주지 않도록 로깅만 한다.
            // 사용자는 GET /calls/incoming 폴링으로 착신을 발견할 수 있다.
            log.error("착신 푸시 발송 실패. callId={}, memberId={}", event.callId(), event.memberId(), e);
        }
    }

    /** 착신 화면에서 대화방으로 이동할 때 쓸 채팅방 ID. 캐릭터 생성 시 방이 함께 만들어져 사실상 항상 있다. */
    private Long resolveChatRoomId(Long relationshipId) {
        return chatRoomRepository.findByRelationshipId(relationshipId)
                .map(ChatRoom::getId)
                .orElse(null);
    }
}
