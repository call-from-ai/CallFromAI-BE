// ⚠ 2026-08-06 TTS를 Typecast로 교체하며 비활성화했다(TypecastVoiceClient/TypecastVoiceProperties).
// 지우지 않고 통째로 주석 처리해 둔 이유는 롤백이다 — Typecast 쪽에 사고가 나면 이 파일의 주석만 풀고,
// application.yml의 clova.voice 블록과 배포 워크플로의 CLOVA_VOICE_CLIENT_ID/SECRET(둘 다 같은 이유로
// 주석으로 남겨 뒀다)을 되살린 뒤 CallAudioWebSocketHandler의 주입 타입만 되돌리면 된다.
// (javadoc 블록 주석이 들어 있어 /* */로는 감쌀 수 없어 줄 주석으로 처리했다)
//
// package com.example.umcCall.domain.call.client;
//
// import org.springframework.boot.context.properties.ConfigurationProperties;
//
// /**
//  * CLOVA Voice(TTS) 접속 설정. application.yml의 {@code clova.voice.*}에서 주입.
//  * STT와 인증 체계가 다르다 — Speech는 gRPC Bearer secretKey, Voice는 NCP API Gateway 키 2개.
//  * (clientId/clientSecret은 환경변수 CLOVA_VOICE_CLIENT_ID / CLOVA_VOICE_CLIENT_SECRET)
//  */
// @ConfigurationProperties(prefix = "clova.voice")
// public record ClovaVoiceProperties(
//         String baseUrl,
//         String clientId,
//         String clientSecret,
//         String format,
//         int samplingRate,
//         // 외부 API가 지연/hang하면 합성 워커가 무한 대기하므로 명시적 상한을 둔다.
//         int connectTimeoutMs,
//         int readTimeoutMs
// ) {
// }
