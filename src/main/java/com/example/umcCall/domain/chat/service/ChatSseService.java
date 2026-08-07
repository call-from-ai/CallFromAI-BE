package com.example.umcCall.domain.chat.service;

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
     * 특정 유저에게 이벤트를 push하고 <b>실제 전송 성공 여부</b>를 반환한다.
     * true = 살아있는 SSE 연결로 전송 성공(라이브 배달됨), false = 연결 없음 또는 죽은 연결로 실패.
     * 호출부는 이 값으로 "SSE로 갔는지 / FCM으로 폴백할지"를 판단한다("맵에 있냐"만 보면 죽은 연결에서 씹힌다).
     */
    public boolean sendToMember(Long memberId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(memberId);
        if (emitter == null) {
            return false;
        }
        return sendTo(emitter, eventName, data);
    }

    /**
     * 15초마다 heartbeat를 보내 연결을 유지하고 죽은 연결을 정리한다.
     */
    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        emitters.forEach((memberId, emitter) -> sendTo(emitter, "heartbeat", "ping"));
    }

    private boolean sendTo(SseEmitter emitter, String eventName, Object data) {
        // heartbeat(스케줄러)·AI 답장(worker)·구독(요청 스레드)이 같은 emitter에 동시에 보낼 수 있다.
        // SseEmitter.send는 스레드 세이프하지 않으므로 emitter 단위로 직렬화한다(유저 간에는 병렬).
        synchronized (emitter) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
                return true;   // 살아있는 연결로 전송 성공
            } catch (Exception e) {
                // 전송 실패 = 끊겼거나 이미 완료된 연결. IOException(broken pipe)뿐 아니라
                // IllegalStateException(이미 error로 완료된 emitter)도 나올 수 있어 Exception으로 폭넓게 잡는다.
                // 여기서 예외를 밖으로 흘리면 호출부(AI 답장 생성 등)가 통째로 중단되므로 절대 전파하지 않는다.
                try {
                    emitter.completeWithError(e);   // 맵 제거 콜백 유도
                } catch (Exception ignored) {
                    // 이미 완료/에러난 emitter면 completeWithError도 던질 수 있다 → 무시한다.
                }
                return false;   // 죽은 연결 → 실패. 호출부(notify)가 이 값을 보고 FCM으로 폴백한다.
            }
        }
    }
}
