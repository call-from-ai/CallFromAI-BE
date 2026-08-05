package com.example.umcCall.global.infra.s3;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * S3 업로드/다운로드/삭제/서명URL 공용 컴포넌트.
 * 채팅 사진·통화 녹음 등 여러 도메인이 재사용하려고 global에 둔다.
 *
 * <p>저장은 <b>객체 key</b>만 다룬다(공개 URL을 만들지 않는다). 객체는 비공개로 두고,
 * 유저에게 내려줄 때만 {@link #presignedGetUrl(String)}로 한시적 서명 URL을 발급한다.
 * 서버가 원본을 다시 읽을 땐({@link #download(String)}) 자격증명으로 직접 받으므로 서명이 필요 없다.
 */
@Component
public class S3Uploader {

    /** 유저에게 내려주는 presigned URL 유효 시간. 채팅 이미지 표시·통화 녹음 재생에 넉넉하도록 60분. */
    private static final Duration PRESIGN_TTL = Duration.ofMinutes(60);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;

    public S3Uploader(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${aws.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
    }

    /**
     * 파일을 dirName 아래에 UUID 이름으로 올리고, 저장용 <b>객체 key</b>를 반환한다.
     * (공개 URL이 아니다. 유저에게 보여줄 땐 {@link #presignedGetUrl(String)}로 서명 URL을 만든다.)
     *
     * @param file    업로드할 파일
     * @param dirName 버킷 내 접두어(예: "chat-photos/12")
     * @return 저장된 객체 key(예: "chat-photos/12/uuid.png")
     */
    public String upload(MultipartFile file, String dirName) {
        String key = dirName + "/" + UUID.randomUUID() + resolveExtension(file.getOriginalFilename());

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            throw new RuntimeException("S3 업로드에 실패했습니다.", e);
        }

        return key;
    }

    /**
     * 서버가 직접 만든 바이트를 올린다(통화 녹음 등). MultipartFile이 없어 확장자·content-type을 호출부가 준다.
     *
     * @return 저장된 객체 key
     */
    public String upload(byte[] content, String dirName, String extension, String contentType) {
        String key = dirName + "/" + UUID.randomUUID() + extension;

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentLength((long) content.length)
                        .build(),
                RequestBody.fromBytes(content));

        return key;
    }

    /**
     * 저장된 key의 객체를 바이트로 내려받는다(예: AI 서버에 원본 파일을 전달할 때).
     * 서버 자격증명으로 직접 받으므로 객체가 비공개여도 동작한다. content-type은 저장 시 값을 그대로 돌려준다.
     * 객체가 없으면 {@link S3ObjectNotFoundException}로 감싸 던진다(호출부가 영구 실패로 구분).
     */
    public S3DownloadedFile download(String key) {
        try {
            ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            return new S3DownloadedFile(object.asByteArray(), object.response().contentType());
        } catch (NoSuchKeyException e) {
            throw new S3ObjectNotFoundException("S3에 객체가 없습니다: " + key, e);
        }
    }

    /**
     * 유저에게 내려줄 한시적 서명 URL(presigned GET URL)을 만든다.
     * 비공개 객체라도 이 URL이면 만료 전까지 열람 가능하다. 네트워크를 타지 않는 로컬 서명이라 가볍다.
     */
    public String presignedGetUrl(String key) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(PRESIGN_TTL)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    /**
     * 저장된 key의 객체를 삭제한다. 이미 없는 객체를 지워도 예외가 나지 않는다(멱등).
     */
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    /** 원본 파일명에서 확장자(.png 등)를 뽑는다. 없으면 빈 문자열. */
    private String resolveExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf("."));
    }
}
