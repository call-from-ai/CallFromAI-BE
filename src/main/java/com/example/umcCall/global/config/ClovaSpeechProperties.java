package com.example.umcCall.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CLOVA Speech gRPC 접속 설정. application.yml의 {@code clova.speech.*}에서 주입.
 * (secretKey는 환경변수 CLOVA_SECRET_KEY)
 */
@ConfigurationProperties(prefix = "clova.speech")
public record ClovaSpeechProperties(String host, int port, String secretKey) {
}
