package com.example.umcCall.domain.call.ticket;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 단명·1회용 WebSocket 티켓 저장소.
 * <p>브라우저 WebSocket API는 커스텀 헤더를 못 붙여 JWT를 핸드셰이크 헤더로 보낼 수 없다.
 * 그래서 REST(POST /calls)는 정상 JWT로 인증하고, URL에는 여기서 발급한 단명 티켓만 실어 보낸다.
 * URL은 로그·히스토리에 남으므로 티켓은 짧은 TTL + 1회용으로 두어 유출 피해를 최소화한다.
 * <p>단일 인스턴스 전제(MVP). 다중 인스턴스로 가면 Redis 등 공유 저장소로 승격한다.
 */
@Component
public class WsTicketStore {

    /** 발급 직후 곧바로 WS를 여는 용도라 짧게 둔다. 튜닝이 필요해지면 yml로 승격. */
    private static final Duration TTL = Duration.ofSeconds(30);

    private final ConcurrentHashMap<String, Entry> tickets = new ConcurrentHashMap<>();

    /**
     * 신원을 담아 티켓을 발급한다. 반환된 문자열을 프론트가 WS URL 쿼리로 되돌려 보낸다.
     * <p>발급 시 만료된 티켓을 함께 청소한다 — 발급됐지만 끝내 소비되지 않은(WS를 안 연) 티켓은
     * {@link #consume} 경로가 못 걷어 맵에 남으므로, 쓰기 경로에서 기회적으로 비워 무한 증가를 막는다.
     * (별도 스케줄러 대신 기회적 청소: 청소량이 발급량에 비례하고 유휴 시엔 돌지 않는다.)
     */
    public String issue(WsTicket ticket) {
        tickets.values().removeIf(Entry::isExpired);
        String raw = UUID.randomUUID().toString();
        tickets.put(raw, new Entry(ticket, Instant.now().plus(TTL)));
        return raw;
    }

    /**
     * 티켓을 검증하며 소비한다. 꺼내는 즉시 삭제(1회용)하고, 만료됐으면 폐기한다.
     * 없음/만료/재사용은 모두 empty로 돌아온다 — 호출부(핸드셰이크)는 그때 연결을 거부한다.
     */
    public Optional<WsTicket> consume(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        // remove가 원자적이라 동시에 두 번 소비돼도 하나만 값을 받는다(1회용 보장).
        Entry entry = tickets.remove(raw);
        if (entry == null || entry.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(entry.ticket());
    }

    private record Entry(WsTicket ticket, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
