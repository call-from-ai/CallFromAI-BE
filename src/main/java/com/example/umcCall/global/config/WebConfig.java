package com.example.umcCall.global.config;

import com.example.umcCall.global.security.TestAccessInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 인터셉터 등록 전용 설정.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final TestAccessInterceptor testAccessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // SecurityConfig에서 permitAll인 "/test/**" 전체를 여기서 구조적으로 보호한다.
        registry.addInterceptor(testAccessInterceptor)
                .addPathPatterns("/test/**");
    }
}