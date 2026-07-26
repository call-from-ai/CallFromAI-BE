package com.example.umcCall.domain.chat.service;

/**
 * AI 이미지 요청이 4xx(잘못된 요청)로 거부됐을 때 던진다.
 * 같은 요청을 다시 보내도 계속 거부되는 "재시도해도 소용없는" 실패라,
 * 디바운서는 이 경우 재시도하지 않고 건너뛴다(무한 재시도 방지).
 */
public class AiImageRequestRejectedException extends RuntimeException {

    public AiImageRequestRejectedException(String message) {
        super(message);
    }
}
