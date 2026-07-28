package com.example.umcCall.domain.auth.client;

import com.example.umcCall.domain.auth.dto.response.KakaoUserResponse;
import com.example.umcCall.domain.auth.exception.AuthErrorCode;
import com.example.umcCall.global.apiPayload.code.GeneralErrorCode;
import com.example.umcCall.global.exception.BaseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KakaoApiClient {

    private final RestClient restClient;

    public KakaoApiClient(
            RestClient.Builder builder,
            @Value("${kakao.base-url}") String baseUrl
    ) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .build();
    }

    public KakaoUserResponse getUserInfo(String kakaoAccessToken) {
        try {
            return restClient.get()
                    .uri("/v2/user/me")
                    .header("Authorization", "Bearer " + kakaoAccessToken)
                    .retrieve()
                    .body(KakaoUserResponse.class);
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new BaseException(AuthErrorCode.INVALID_KAKAO_TOKEN);
        } catch (RestClientException e) {
            throw new BaseException(GeneralErrorCode.EXTERNAL_API_ERROR, "카카오 사용자 정보 조회에 실패했습니다.");
        }
    }
}