package com.example.umcCall.global.infra.s3;

/**
 * S3에 해당 객체가 없을 때 던진다(예: 사진이 이미 삭제됨).
 * 재시도해도 복구되지 않는 "영구 실패"라, 호출부는 이 경우 재시도 대신 건너뛰어야 한다.
 */
public class S3ObjectNotFoundException extends RuntimeException {

    public S3ObjectNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
