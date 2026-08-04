package com.example.umcCall.domain.chat.service;

import com.example.umcCall.domain.chat.dto.response.ChatMessageResponse;
import com.example.umcCall.domain.chat.dto.response.ChatRoomSignalResponse;
import com.example.umcCall.domain.chat.entity.ChatMessage;
import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.port.CharacterSummary;
import com.example.umcCall.domain.chat.port.CharacterSummaryProvider;
import com.example.umcCall.domain.notification.push.dto.PushMessage;
import com.example.umcCall.domain.notification.push.service.PushNotificationService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AI 채팅 메시지를 유저에게 배달하는 공용 진입점(선제연락·AI 답장 디바운서가 함께 사용).
 * - SSE로 접속 중이면(앱 켜짐) SSE로 라이브 전달.
 * - 접속 안 했고(앱 꺼짐) 방 음소거도 아니면 FCM 푸시.
 * 음소거는 FCM만 막는다("폰 알림 끄기"지 메시지 숨김이 아니므로 SSE 라이브 전달은 유지).
 * 반드시 메시지 저장 트랜잭션이 커밋된 뒤에 호출한다(롤백 시 유령 알림 방지).
 */
@Component
@RequiredArgsConstructor
public class ChatMessageNotifier {

    // SSE 이벤트 이름. "error"는 브라우저 EventSource가 연결 오류용으로 이미 쓰므로 "chat-error"로 구분한다.
    private static final String EVENT_MESSAGE = "message";
    private static final String EVENT_LOADING = "loading";
    private static final String EVENT_ERROR = "chat-error";

    private static final String PHOTO_PREVIEW = "[사진]";
    private static final String FALLBACK_TITLE = "새 메시지";
    // 푸시 본문 미리보기 최대 길이. 배너 표시엔 충분하고 FCM 페이로드 한도(약 4KB)보다 한참 아래로 둔다.
    private static final int PREVIEW_MAX_LENGTH = 100;

    private final ChatSseService chatSseService;
    private final PushNotificationService pushNotificationService;
    private final CharacterSummaryProvider characterSummaryProvider;

    public void notify(ChatRoom room, ChatMessage message) {
        Long memberId = room.getMemberId();
        if (chatSseService.isConnected(memberId)) {
            chatSseService.sendToMember(memberId, EVENT_MESSAGE, ChatMessageResponse.from(message));
            return;
        }
        if (room.isMuted()) {
            return;   // 앱 꺼짐 + 음소거 → 푸시 안 함
        }
        pushNotificationService.send(memberId,
                PushMessage.chat(room.getId(), resolveCharacterName(room.getRelationshipId()), preview(message)));
    }

    /**
     * AI가 답장을 만들기 시작했음을 알린다("…" 입력중 표시 켜기).
     * 로딩 표시는 채팅 화면을 보고 있을 때만 의미가 있어 SSE 접속 중일 때만 전달한다(FCM 없음).
     * sendToMember는 미접속이면 스스로 아무것도 하지 않으므로 별도 접속 체크는 생략한다.
     */
    public void notifyLoading(ChatRoom room) {
        chatSseService.sendToMember(room.getMemberId(), EVENT_LOADING, new ChatRoomSignalResponse(room.getId()));
    }

    /**
     * AI 답장 생성이 실패했음을 알린다("…" 표시 걷기). 저장하지 않는 순간 신호라 방 번호만 보낸다.
     * loading과 짝을 이뤄, 로딩을 켠 뒤 답장이 못 나오는 모든 경우에 이걸로 표시를 닫는다.
     */
    public void notifyError(ChatRoom room) {
        chatSseService.sendToMember(room.getMemberId(), EVENT_ERROR, new ChatRoomSignalResponse(room.getId()));
    }

    /** 푸시 배너 제목용 캐릭터 이름. 관계가 없거나 조회 실패면 기본값. */
    private String resolveCharacterName(Long relationshipId) {
        if (relationshipId == null) {
            return FALLBACK_TITLE;
        }
        Map<Long, CharacterSummary> summaries = characterSummaryProvider.getSummaries(List.of(relationshipId));
        CharacterSummary summary = summaries.get(relationshipId);
        return summary != null ? summary.characterFirstName() : FALLBACK_TITLE;
    }

    /** 푸시 본문 미리보기. 사진뿐이면 "[사진]", 텍스트가 길면 잘라서(전송 실패·배너 낭비 방지) 말줄임표를 붙인다. */
    private String preview(ChatMessage message) {
        String content = message.getContent();
        if (content == null || content.isBlank()) {
            return PHOTO_PREVIEW;
        }
        return content.length() > PREVIEW_MAX_LENGTH
                ? content.substring(0, PREVIEW_MAX_LENGTH) + "…"
                : content;
    }
}
