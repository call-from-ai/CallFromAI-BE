package com.example.umcCall.global.infra.fcm;

import com.example.umcCall.domain.notification.push.dto.PushMessage;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * FirebaseMessaging을 감싸는 얇은 FCM 발송기
 * PushMessage 하나를 여러 기기 토큰에 멀티캐스트로 보내고, 무효(삭제 대상) 토큰 목록을 돌려준다.
 */
@Slf4j
@Component
public class FcmSender {

    private static final String ANDROID_CHANNEL_ID = "general";
    private static final String ANDROID_SOUND = "default";

    private final FirebaseMessaging firebaseMessaging;

    public FcmSender(@Nullable FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    /**
     * 여러 토큰에 전송하고, 응답에서 영구 무효로 판정된 토큰 목록을 반환한다(호출자가 DB에서 정리).
     * FCM 비활성이거나 토큰이 없으면 아무것도 하지 않는다.
     */
    public List<String> send(List<String> tokens, PushMessage message) {
        if (firebaseMessaging == null || tokens.isEmpty()) {
            return List.of();
        }
        try {
            BatchResponse response = firebaseMessaging.sendEachForMulticast(buildMulticast(tokens, message));
            return collectInvalidTokens(tokens, response);
        } catch (FirebaseMessagingException e) {
            // 배치 자체 실패(네트워크·인증 등)는 특정 토큰 문제로 볼 수 없으므로 아무 토큰도 지우지 않는다.
            log.error("FCM 멀티캐스트 전송 실패. type={}, errorCode={}",
                    message.getType(), e.getMessagingErrorCode(), e);
            return List.of();
        }
    }

    private MulticastMessage buildMulticast(List<String> tokens, PushMessage message) {
        MulticastMessage.Builder builder = MulticastMessage.builder()
                .addAllTokens(tokens)
                .putData("type", message.getType().name())
                .putAllData(message.getData())
                .setAndroidConfig(buildAndroidConfig(message));
        // title이 있으면 OS가 배너를 자동 표시(CHAT/NOTICE). CALL은 title=null → 배너 없이 data-only.
        if (message.getTitle() != null) {
            builder.setNotification(Notification.builder()
                    .setTitle(message.getTitle())
                    .setBody(message.getBody())
                    .build());
        }
        return builder.build();
    }

    private AndroidConfig buildAndroidConfig(PushMessage message) {
        AndroidConfig.Builder android = AndroidConfig.builder()
                .setPriority(message.isHighPriority()
                        ? AndroidConfig.Priority.HIGH
                        : AndroidConfig.Priority.NORMAL);
        // 배너가 있는 경우(CHAT/NOTICE)만 채널·사운드를 지정한다. CALL은 data-only라 불필요.
        if (message.getTitle() != null) {
            android.setNotification(AndroidNotification.builder()
                    .setChannelId(ANDROID_CHANNEL_ID)
                    .setSound(ANDROID_SOUND)
                    .build());
        }
        return android.build();
    }

    /** 멀티캐스트 응답에서 UNREGISTERED(앱 삭제 등)·INVALID_ARGUMENT 토큰을 골라낸다(=삭제 대상). */
    private List<String> collectInvalidTokens(List<String> tokens, BatchResponse response) {
        List<String> invalidTokens = new ArrayList<>();
        List<SendResponse> responses = response.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse sendResponse = responses.get(i);
            if (sendResponse.isSuccessful()) {
                continue;
            }
            MessagingErrorCode errorCode = sendResponse.getException().getMessagingErrorCode();
            if (errorCode == MessagingErrorCode.UNREGISTERED
                    || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                invalidTokens.add(tokens.get(i));
            }
        }
        return invalidTokens;
    }
}
