package com.example.umcCall.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.IOException;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * FCM(Firebase Cloud Messaging) 초기화 설정.
 * resources 아래 서비스계정 JSON 키를 classpath로 읽어 FirebaseApp을 1회 초기화하고
 * FirebaseMessaging 빈을 등록한다.
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${fcm.credentials-path:firebase/service-account.json}")
    private String credentialsPath;

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        ClassPathResource resource = new ClassPathResource(credentialsPath);
        if (!resource.exists()) {
            log.warn("FCM 크리덴셜(classpath:{})이 없어 FCM 비활성으로 부팅합니다.", credentialsPath);
            return null;
        }
        try (InputStream credentials = resource.getInputStream()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .build();
            // 이미 초기화된 앱이 있으면 재사용한다(테스트·컨텍스트 재기동 시 중복 init 방지).
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();
            log.info("FCM 초기화 완료. FirebaseMessaging 빈을 등록한다.");
            return FirebaseMessaging.getInstance(app);
        } catch (IOException e) {
            // 파일은 있는데 읽기·파싱에 실패한 경우는 설정 오류이므로 부팅을 실패시켜 빠르게 드러낸다.
            throw new IllegalStateException("FCM 크리덴셜 로딩 실패: classpath:" + credentialsPath, e);
        }
    }
}
