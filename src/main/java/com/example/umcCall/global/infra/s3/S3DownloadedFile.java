package com.example.umcCall.global.infra.s3;

/**
 * S3에서 내려받은 파일. 바이트와 (S3에 저장된) content-type을 함께 담는다.
 *
 * @param bytes       파일 바이트
 * @param contentType 업로드 시 저장된 MIME 타입(예: image/jpeg). null일 수 있다.
 */
public record S3DownloadedFile(
        byte[] bytes,
        String contentType
) {
}
