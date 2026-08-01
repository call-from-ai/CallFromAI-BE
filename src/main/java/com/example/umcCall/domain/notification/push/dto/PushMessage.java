package com.example.umcCall.domain.notification.push.dto;

import com.example.umcCall.domain.notification.push.enums.PushType;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;

/**
 * FCM으로 보낼 한 건의 푸시 내용. 호출하는 도메인(채팅/통화/알림)은 아래 정적 팩토리만 골라 쓰면
 * FE와 합의한 형식(type·data·우선순위·배너 유무)대로 포장된다. FCM 세부는 FcmSender가 처리한다.
 *
 * - title/body가 있으면 OS 배너로 표시되고(CHAT/NOTICE), null이면 data-only로 앱이 직접 그린다(CALL).
 * - data 값은 FCM 규격상 모두 문자열이다.
 */
@Getter
public class PushMessage {

    private final PushType type;
    private final String title;                 // null이면 data-only(배너 없음)
    private final String body;                  // null 가능
    private final Map<String, String> data;     // type 외 부가 데이터(식별자 등)
    private final boolean highPriority;         // CALL 등 즉시성 필요 시 true

    private PushMessage(PushType type, String title, String body,
                        Map<String, String> data, boolean highPriority) {
        this.type = type;
        this.title = title;
        this.body = body;
        this.data = data;
        this.highPriority = highPriority;
    }

    /** 채팅 알림. 클릭 시 chatRoomId 방으로 이동한다. */
    public static PushMessage chat(Long chatRoomId, String title, String body) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("chatRoomId", String.valueOf(chatRoomId));
        return new PushMessage(PushType.CHAT, title, body, data, false);
    }

    /**
     * 전화 착신 알림. 수락/거절 커스텀 UI라 배너 없이 data-only + 高우선순위로 보낸다.
     *
     * <p>⚠ {@code characterImageUrl}은 이미지 없이 만든 캐릭터면 null인데, FCM data 맵은 null 값을
     * 허용하지 않는다(전송이 아니라 <b>메시지 조립 단계</b>에서 "null value in entry" NPE).
     * 빈 문자열로 채워 FE와 합의한 키 5개를 항상 유지한다.
     */
    public static PushMessage call(Long callId, Long characterId, String characterName,
                                   String characterImageUrl, Long chatRoomId) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("callId", String.valueOf(callId));
        data.put("characterId", String.valueOf(characterId));
        data.put("characterName", characterName);
        data.put("characterImageUrl", characterImageUrl == null ? "" : characterImageUrl);
        data.put("chatRoomId", String.valueOf(chatRoomId));
        return new PushMessage(PushType.CALL, null, null, data, true);
    }

    /** 공지·지난 알림(기념일/통화약속/부재중 등) 배너. */
    public static PushMessage notice(String title, String body) {
        return new PushMessage(PushType.NOTICE, title, body, new LinkedHashMap<>(), false);
    }
}
