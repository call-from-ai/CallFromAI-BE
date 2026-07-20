package com.example.umcCall.domain.call.ticket;

import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * WebSocket 핸드셰이크에서 wsTicket을 검증한다.
 * <p>핸드셰이크는 HTTP GET이라 서블릿 필터(Security)를 타지만, 브라우저 WS는 JWT 헤더를 못 실어
 * 그 경로는 permitAll로 열어둔다. 대신 여기서 URL 쿼리의 티켓을 소비·검증해 인증을 대신한다.
 * 검증된 신원({@link WsTicket})을 세션 attributes에 실어 핸들러가 {@code ActiveCall}에 바인딩한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsTicketHandshakeInterceptor implements HandshakeInterceptor {

    /** 검증된 신원이 실리는 세션 속성 키. 핸들러가 이 키로 꺼낸다. */
    public static final String WS_TICKET_ATTRIBUTE = "wsTicket";

    private final WsTicketStore wsTicketStore;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String raw = UriComponentsBuilder.fromUri(request.getURI())
                .build().getQueryParams().getFirst("ticket");

        Optional<WsTicket> ticket = wsTicketStore.consume(raw);
        if (ticket.isEmpty()) {
            // 없음/만료/재사용 → 핸드셰이크 거부. 소켓은 열리지 않는다.
            log.warn("[Call] wsTicket 검증 실패 → 핸드셰이크 거부.");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(WS_TICKET_ATTRIBUTE, ticket.get());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
