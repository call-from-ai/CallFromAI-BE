package com.example.umcCall.domain.chat.service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 채팅 실시간 전달용 SSE 연결 관리.
 * AI가 아무 방에나 먼저 발신할 수 있어, 방이 아니라 "유저" 단위로 연결을 하나만 유지한다.
 * 여러 스레드가 동시에 접근하므로 ConcurrentHashMap을 쓴다.
 */
@Slf4j
@Service
public class ChatSseService {

    // 30분 뒤 연결을 스스로 닫아 재연결시킴 앱은 끊기면 자동 재연결.
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 유저의 SSE 구독 연결을 생성한다. 기존 연결이 있으면 교체한다(유저당 1개).
     */
    public SseEmitter subscribe(Long memberId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);

        SseEmitter old = emitters.put(memberId, emitter);
        if (old != null) {
            old.complete();   // 중복 연결 방지: 기존 연결 종료
        }

        // 연결이 끝나면(정상완료/타임아웃/에러) 맵에서 제거해 메모리 누수를 막는다.
        // key+value 조건부 제거라, 이미 새 연결로 교체된 경우엔 지우지 않는다.
        emitter.onCompletion(() -> emitters.remove(memberId, emitter));
        emitter.onTimeout(emitter::complete);   // complete되면 onCompletion에서 제거
        emitter.onError(e -> emitters.remove(memberId, emitter));

        // 최초 연결 직후 더미 이벤트: 첫 데이터가 없으면 재연결 시 오류가 날 수 있어 한 번 보낸다.
        sendTo(emitter, "connect", "connected");

        return emitter;
    }

    /**
     * 특정 유저에게 이벤트를 push
     */
    public void sendToMember(Long memberId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(memberId);
        if (emitter == null) {
            return;
        }
        sendTo(emitter, eventName, data);
    }

    /**
     * 15초마다 heartbeat를 보내 연결을 유지하고 죽은 연결을 정리한다.
     */
    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        emitters.forEach((memberId, emitter) -> sendTo(emitter, "heartbeat", "ping"));
    }

    private void sendTo(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            // 전송 실패 = 끊긴 연결 → 에러로 완료 처리(→ 콜백에서 맵 제거)
            emitter.completeWithError(e);
        }
    }
}
