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
     * <p>⚠ {@code characterImageUrl}·{@code chatRoomId}는 없을 수 있는데, 둘 다 <b>키를 빼거나
     * 발송을 막지 않고 빈 문자열로 채운다</b> — FE와 합의한 키 5개를 항상 유지해 분기를 하나로 두고,
     * 부가 정보가 없다고 벨을 생략해 사용자가 실제 착신을 놓치는 일을 막기 위해서다(못 받은 통화는
     * 벨 타임아웃 뒤 {@code MISSED}로만 남는다). FE는 빈 값이면 해당 기능만 비활성화하면 된다.
     *
     * <p>⚠ 빈 문자열 변환을 지우면 안 된다. {@code null}을 넣으면 두 가지로 깨진다 —
     * {@code characterImageUrl}은 FCM data 맵이 null 값을 거부해 <b>메시지 조립 단계</b>에서
     * "null value in entry" NPE로 푸시가 통째로 안 나가고, {@code chatRoomId}는 {@code Long}이라
     * {@code String.valueOf(Object)}에 묶여 <b>리터럴 문자열 {@code "null"}</b>이 조용히 실려 나간다
     * (FE가 파싱하면 착신 화면이 깨진다 — 전송은 성공해서 서버 로그엔 아무 흔적이 없다).
     */
    public static PushMessage call(Long callId, Long characterId, String characterName,
                                   String characterImageUrl, Long chatRoomId) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("callId", String.valueOf(callId));
        data.put("characterId", String.valueOf(characterId));
        data.put("characterName", characterName);
        data.put("characterImageUrl", characterImageUrl == null ? "" : characterImageUrl);
        data.put("chatRoomId", chatRoomId == null ? "" : String.valueOf(chatRoomId));
        return new PushMessage(PushType.CALL, null, null, data, true);
    }

    /** 공지·지난 알림(기념일/통화약속/부재중 등) 배너. */
    public static PushMessage notice(String title, String body) {
        return new PushMessage(PushType.NOTICE, title, body, new LinkedHashMap<>(), false);
    }
}
