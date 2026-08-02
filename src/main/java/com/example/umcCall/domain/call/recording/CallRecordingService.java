package com.example.umcCall.domain.call.recording;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 마감된 통화 녹음을 보관한다(S3 업로드 + {@code Call.audioUrl}·{@code recordingStatus} 저장).
 *
 * <p>WS 핸들러가 통화 정리 경로에서 부른다 — 핸들러는 "다 모았다"까지만 알고, 어디에 어떻게 두는지는
 * 여기가 정한다.
 *
 * <p>⚠ <b>업로드 본체는 아직 없다</b>(#125 단계 D). S3 버킷을 녹음 prefix만 비공개로 바꾸기 전에는
 * 올리면 안 되기 때문이다 — 지금 올리면 그 녹음이 퍼블릭으로 남고, 나중에 정책을 바꿔도 회수할 수 없다.
 */
@Slf4j
@Service
public class CallRecordingService {

    /**
     * 녹음 하나를 보관한다. <b>실패해도 예외를 던지지 않는다</b> — 통화는 이미 끝났고,
     * 녹음이 없다고 통화·전사가 달라지지 않는다(fail-open).
     */
    public void save(Long callId, byte[] wav) {
        log.info("[Recording] 녹음 마감. callId={}, bytes={} (업로드는 단계 D)", callId, wav.length);
    }
}
