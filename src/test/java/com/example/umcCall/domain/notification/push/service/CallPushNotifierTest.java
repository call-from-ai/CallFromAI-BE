package com.example.umcCall.domain.notification.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.umcCall.domain.call.event.CallRingingEvent;
import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.repository.ChatRoomRepository;
import com.example.umcCall.domain.notification.push.dto.PushMessage;
import com.example.umcCall.domain.notification.push.enums.PushType;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CallPushNotifierTest {

    private static final long CALL_ID = 1L;
    private static final long RELATIONSHIP_ID = 10L;
    private static final long MEMBER_ID = 20L;
    private static final long CHARACTER_ID = 30L;
    private static final long CHAT_ROOM_ID = 40L;
    private static final String CHARACTER_NAME = "지호";
    private static final String CHARACTER_IMAGE_URL = "https://cdn.example.com/character/30.png";

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private PushNotificationService pushNotificationService;

    @InjectMocks
    private CallPushNotifier callPushNotifier;

    @Test
    void 착신_이벤트를_받으면_CALL_푸시를_보낸다() {
        ChatRoom chatRoom = mock(ChatRoom.class);
        when(chatRoom.getId()).thenReturn(CHAT_ROOM_ID);
        when(chatRoomRepository.findByRelationshipId(RELATIONSHIP_ID)).thenReturn(Optional.of(chatRoom));

        callPushNotifier.onCallRinging(ringingEvent(CHARACTER_IMAGE_URL));

        PushMessage message = capturePushMessage();
        assertThat(message.getType()).isEqualTo(PushType.CALL);
        // data-only + 高우선순위 — 수락/거절 UI를 앱이 직접 그린다.
        assertThat(message.getTitle()).isNull();
        assertThat(message.isHighPriority()).isTrue();
        assertThat(message.getData()).containsOnly(
                entry("callId", String.valueOf(CALL_ID)),
                entry("characterId", String.valueOf(CHARACTER_ID)),
                entry("characterName", CHARACTER_NAME),
                entry("characterImageUrl", CHARACTER_IMAGE_URL),
                entry("chatRoomId", String.valueOf(CHAT_ROOM_ID)));
    }

    /** 이미지 없는 캐릭터가 정상 경로인데, null을 그대로 넣으면 NPE로 푸시가 통째로 안 나간다. */
    @Test
    void 캐릭터_이미지가_없어도_키를_빈_값으로_실어_보낸다() {
        ChatRoom chatRoom = mock(ChatRoom.class);
        when(chatRoom.getId()).thenReturn(CHAT_ROOM_ID);
        when(chatRoomRepository.findByRelationshipId(RELATIONSHIP_ID)).thenReturn(Optional.of(chatRoom));

        callPushNotifier.onCallRinging(ringingEvent(null));

        PushMessage message = capturePushMessage();
        assertThat(message.getData())
                .containsEntry("characterImageUrl", "")
                .doesNotContainValue(null);
    }

    /** 통화 생성은 이미 커밋됐다 — 푸시가 터져도 통화는 살아 있어야 하고, 폴링으로 받을 수 있다. */
    @Test
    void 푸시_발송이_실패해도_예외를_전파하지_않는다() {
        when(chatRoomRepository.findByRelationshipId(RELATIONSHIP_ID))
                .thenThrow(new RuntimeException("DB 장애"));

        assertThatCode(() -> callPushNotifier.onCallRinging(ringingEvent(CHARACTER_IMAGE_URL)))
                .doesNotThrowAnyException();
    }

    private PushMessage capturePushMessage() {
        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushNotificationService).send(eq(MEMBER_ID), captor.capture());
        return captor.getValue();
    }

    private CallRingingEvent ringingEvent(String characterImageUrl) {
        return new CallRingingEvent(
                CALL_ID, RELATIONSHIP_ID, MEMBER_ID, CHARACTER_ID, CHARACTER_NAME, characterImageUrl);
    }
}
