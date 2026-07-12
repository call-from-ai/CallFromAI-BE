package com.example.umcCall.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CLOVA Speech 실시간 스트리밍(gRPC) 접속 설정. (CLAUDE.md 4장)
 * host/port/secret-key 3개가 항상 함께 gRPC 채널 생성에 쓰이므로 하나로 묶는다.
 * 값은 application.yml의 {@code clova.speech.*}에서 주입 (secretKey는 환경변수 CLOVA_SECRET_KEY).
 *
 * @param host      gRPC 게이트웨이 호스트 (예: clovaspeech-gw.ncloud.com)
 * @param port      gRPC 포트 (예: 50051)
 * @param secretKey 콘솔 발급 secret key. 메타데이터 {@code authorization: Bearer {secretKey}} 로 전송 (2-3).
 */
@ConfigurationProperties(prefix = "clova.speech")
public record ClovaSpeechProperties(String host, int port, String secretKey) {
}
