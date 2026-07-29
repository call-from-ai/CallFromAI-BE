package com.example.umcCall.domain.chat.service;

import com.example.umcCall.domain.chat.dto.response.ChatMessageResponse;
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

    private static final String PHOTO_PREVIEW = "[사진]";
    private static final String FALLBACK_TITLE = "새 메시지";

    private final ChatSseService chatSseService;
    private final PushNotificationService pushNotificationService;
    private final CharacterSummaryProvider characterSummaryProvider;

    public void notify(ChatRoom room, ChatMessage message) {
        Long memberId = room.getMemberId();
        if (chatSseService.isConnected(memberId)) {
            chatSseService.sendToMember(memberId, "message", ChatMessageResponse.from(message));
            return;
        }
        if (room.isMuted()) {
            return;   // 앱 꺼짐 + 음소거 → 푸시 안 함
        }
        pushNotificationService.send(memberId,
                PushMessage.chat(room.getId(), resolveCharacterName(room.getRelationshipId()), preview(message)));
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

    /** 푸시 본문. 텍스트가 있으면 그대로, 사진뿐이면 "[사진]". */
    private String preview(ChatMessage message) {
        String content = message.getContent();
        return (content != null && !content.isBlank()) ? content : PHOTO_PREVIEW;
    }
}
