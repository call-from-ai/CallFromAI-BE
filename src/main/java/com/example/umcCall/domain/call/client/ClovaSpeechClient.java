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
 * CLOVA Speech 실시간 스트리밍(gRPC) 배관. (CLAUDE.md 4장, 5장 2-3)
 *
 * <p>앱 전체가 공유하는 {@link ManagedChannel} 하나를 CLOVA 게이트웨이로 열어두고,
 * 세션마다 {@code recognize} 양방향 스트림을 개설해준다. STT 인식은 CLOVA가 하며,
 * 이 클래스는 "연결/스트림 개설" 배관만 담당한다. (STT 직접 구현 안 함 — CLAUDE.md 7장)
 */
@Slf4j
@Component
public class ClovaSpeechClient {

    /** 인증 메타데이터 키. CLOVA 요구사항상 소문자 {@code authorization} 필수. (CLAUDE.md 4장) */
    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final ClovaSpeechProperties properties;
    private ManagedChannel channel;

    public ClovaSpeechClient(ClovaSpeechProperties properties) {
        this.properties = properties;
    }

    @PostConstruct // 빈 생성 직후
    void openChannel() {
        // 50051은 secure 채널. (CLAUDE.md 4장)
        this.channel = NettyChannelBuilder.forAddress(properties.host(), properties.port())
                .useTransportSecurity()
                .build();
        log.info("[Clova] gRPC 채널 생성. host={}, port={}", properties.host(), properties.port());
    }

    @PreDestroy // 빈 소멸 직전
    void closeChannel() {
        if (channel != null) {
            channel.shutdown();
            log.info("[Clova] gRPC 채널 종료.");
        }
    }

    /**
     * 세션 하나의 {@code recognize} 양방향 스트림을 연다.
     *
     * <p>gRPC 양방향 스트림 규약상 <b>응답 옵저버를 먼저 넘겨야</b>(응답이 올 곳을 등록해야)
     * 요청을 밀어넣을 업스트림 옵저버가 반환된다. 호출자는 반환된 옵저버로
     * {@code CONFIG 1회 → DATA N회 → onCompleted()} 순서로 흘려보내면 된다.
     *
     * @param responseObserver CLOVA 인식 결과(NestResponse)를 받을 콜백
     * @return CLOVA로 요청(NestRequest)을 보낼 업스트림 옵저버
     */
    public StreamObserver<NestRequest> openRecognizeStream(StreamObserver<NestResponse> responseObserver) {
        Metadata metadata = new Metadata();
        metadata.put(AUTHORIZATION, "Bearer " + properties.secretKey());

        NestServiceGrpc.NestServiceStub stub = NestServiceGrpc.newStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));

        return stub.recognize(responseObserver);
    }
}
