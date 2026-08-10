package com.example.umcCall.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class TestAccessInterceptor implements HandlerInterceptor {

    private static final String SECRET_HEADER = "X-Test-Secret";

    private final TestAccessGuard testAccessGuard;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        testAccessGuard.check(request.getHeader(SECRET_HEADER));
        return true;
    }
}