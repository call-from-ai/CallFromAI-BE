package com.example.umcCall.global.security;

import com.example.umcCall.domain.auth.exception.AuthErrorCode;
import com.example.umcCall.global.apiPayload.code.GeneralErrorCode;
import com.example.umcCall.global.exception.BaseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * "/test/**" 아래 디버그·테스트 API에 대한 접근을 제어한다.
 *
 * <p>{@code test.access.secret}(환경변수 {@code TEST_ACCESS_SECRET})이 설정되어 있으면,
 * 요청 헤더 {@code X-Test-Secret} 값이 이 값과 정확히 일치해야만 통과시킨다.
 * 값이 비어 있으면(= 별도 설정을 하지 않은 로컬 개발 환경) 제한 없이 통과시켜
 * 로컬 개발 편의성을 그대로 유지한다.
 *
 * <p><b>운영(prod) 배포 시에는 반드시 GitHub Actions Secrets에 {@code TEST_ACCESS_SECRET}을
 * 설정해야 한다.</b> 설정하지 않으면 이 가드는 사실상 아무 제한도 걸지 않는다 — 즉 이전과
 * 동일하게 누구나 테스트 로그인/디버그 API를 호출할 수 있는 상태가 그대로 유지된다.
 *
 * <p>이 클래스는 Spring Security의 인증/인가 체계를 대체하지 않는다. {@code SecurityConfig}에서
 * "/test/**"는 여전히 permitAll이며, 이 가드는 그 위에 얹는 별도의 공유 비밀키 검증 계층이다.
 * (테스트 로그인 자체가 "인증 전" 단계에서 호출돼야 하므로 JWT 기반 인가로는 보호할 수 없다.)
 */
@Component
public class TestAccessGuard {

    @Value("${test.access.secret:}")
    private String configuredSecret;

    public void check(String providedSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            // 시크릿이 설정되지 않은 환경(주로 로컬) — 제한 없이 통과
            return;
        }
        if (!configuredSecret.equals(providedSecret)) {
            throw new BaseException(AuthErrorCode.ACCESS_DENIED, "테스트 API 접근 시크릿이 올바르지 않습니다.");
        }
    }
}
 