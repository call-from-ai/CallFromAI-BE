package com.example.umcCall.global.infra.s3;

import java.io.IOException;
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

/**
 * S3 업로드/삭제 공용 컴포넌트.
 * 채팅 사진뿐 아니라 다른 도메인의 이미지 업로드에도 재사용하기 위해 global에 둔다.
 * 버킷이 전체 퍼블릭 읽기라, 파일명을 UUID로 만들어 URL 추측을 어렵게 한다.
 */
@Component
public class S3Uploader {

    private final S3Client s3Client;
    private final String bucket;
    private final String region;

    public S3Uploader(
            S3Client s3Client,
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.s3.region}") String region) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.region = region;
    }

    /**
     * 파일을 dirName 아래에 UUID 이름으로 올리고, 접근 가능한 URL을 반환한다.
     *
     * @param file    업로드할 파일
     * @param dirName 버킷 내 접두어(예: "chat-photos/12")
     * @return 프리셋과 동일한 형식의 객체 URL
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

        return toUrl(key);
    }

    /**
     * 서버가 직접 만든 바이트를 올린다(통화 녹음 등). MultipartFile이 없어 확장자·content-type을 호출부가 준다.
     * 이름은 {@link #upload(MultipartFile, String)}과 같이 UUID라, 퍼블릭 버킷에서 URL 추측을 어렵게 한다.
     *
     * @return 접근 가능한 객체 URL(채팅 사진과 동일한 형식)
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

        return toUrl(key);
    }

    /**
     * 저장된 URL의 객체를 바이트로 내려받는다(예: AI 서버에 원본 파일을 전달할 때).
     * content-type은 업로드 시 저장된 값을 그대로 돌려준다.
     * 객체가 없거나 접근 실패 시 AWS SDK 예외가 그대로 전파된다(호출부가 처리).
     */
    public S3DownloadedFile download(String fileUrl) {
        String key = extractKey(fileUrl);
        try {
            ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            return new S3DownloadedFile(object.asByteArray(), object.response().contentType());
        } catch (NoSuchKeyException e) {
            // 객체가 없으면 재시도해도 안 되는 영구 실패 → 호출부가 구분할 수 있게 감싸 던진다.
            throw new S3ObjectNotFoundException("S3에 객체가 없습니다: " + key, e);
        }
    }

    /**
     * 저장된 URL에서 객체 key를 역산해 삭제한다. 이미 없는 객체를 지워도 예외가 나지 않는다(멱등).
     */
    public void delete(String fileUrl) {
        String key = extractKey(fileUrl);
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    /** 프리셋 이미지와 동일한 형식의 퍼블릭 URL을 만든다. */
    private String toUrl(String key) {
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    }

    /** URL에서 버킷 도메인 뒤쪽(=객체 key)만 잘라낸다. */
    private String extractKey(String fileUrl) {
        String prefix = "https://" + bucket + ".s3." + region + ".amazonaws.com/";
        return fileUrl.replace(prefix, "");
    }

    /** 원본 파일명에서 확장자(.png 등)를 뽑는다. 없으면 빈 문자열. */
    private String resolveExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf("."));
    }
}
