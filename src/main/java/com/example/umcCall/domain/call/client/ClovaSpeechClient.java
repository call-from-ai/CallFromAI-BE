package com.example.umcCall.domain.call.client;

import com.example.umcCall.global.config.ClovaSpeechProperties;
import com.nbp.cdncp.nest.grpc.proto.v1.NestRequest;
import com.nbp.cdncp.nest.grpc.proto.v1.NestResponse;
import com.nbp.cdncp.nest.grpc.proto.v1.NestServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * CLOVA Speech gRPC 배관. 앱 공유 {@link ManagedChannel} 하나를 열어두고,
 * 세션마다 {@code recognize} 양방향 스트림을 개설해준다. (STT 인식은 CLOVA 담당)
 */
@Slf4j
@Component
public class ClovaSpeechClient {

    /** 인증 헤더 키. (gRPC가 전송 시 소문자로 정규화하므로 리터럴 대소문자는 무관) */
    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final ClovaSpeechProperties properties;
    private ManagedChannel channel;

    public ClovaSpeechClient(ClovaSpeechProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void openChannel() {
        this.channel = NettyChannelBuilder.forAddress(properties.host(), properties.port())
                .useTransportSecurity() // CLOVA gateway는 TLS 필수
                .build();
        log.info("[Clova] gRPC 채널 생성. host={}, port={}", properties.host(), properties.port());
    }

    @PreDestroy
    void closeChannel() {
        if (channel != null) {
            channel.shutdown();
            log.info("[Clova] gRPC 채널 종료.");
        }
    }

    /**
     * 세션 하나의 {@code recognize} 양방향 스트림을 연다.
     * 응답 옵저버를 넘기면 요청용 업스트림 옵저버가 반환된다.
     * 호출자는 {@code CONFIG 1회 → DATA N회 → onCompleted()} 순서로 보낸다.
     */
    public StreamObserver<NestRequest> openRecognizeStream(StreamObserver<NestResponse> responseObserver) {
        Metadata metadata = new Metadata();
        metadata.put(AUTHORIZATION, "Bearer " + properties.secretKey());

        NestServiceGrpc.NestServiceStub stub = NestServiceGrpc.newStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));

        return stub.recognize(responseObserver);
    }
}
