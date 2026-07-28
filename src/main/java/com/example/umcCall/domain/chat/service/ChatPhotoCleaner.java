package com.example.umcCall.domain.chat.service;

import com.example.umcCall.domain.chat.repository.ChatPhotoRepository;
import com.example.umcCall.global.infra.s3.S3Uploader;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 채팅 사진을 DB와 S3에서 함께 정리하는 컴포넌트(캐릭터 하드 삭제·개별 메시지 삭제 시 사용).
 * chat_photo는 호출한 트랜잭션에서 지우고, S3 객체는 커밋이 확정된 뒤에만 지운다.
 * (트랜잭션 안에서 S3를 지우면 롤백 시 파일만 사라지므로.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatPhotoCleaner {

    private final ChatPhotoRepository chatPhotoRepository;
    private final S3Uploader s3Uploader;

    /** 방의 모든 사진을 정리한다. 반드시 트랜잭션 안에서 호출한다(afterCommit이 동작해야 하므로). */
    public void purgeRoomPhotos(Long roomId) {
        List<String> photoUrls = chatPhotoRepository.findPhotoUrlsByChatRoomId(roomId);
        chatPhotoRepository.deleteByChatRoomId(roomId);   // DB 삭제는 이 트랜잭션에서
        scheduleS3Delete(photoUrls);
    }

    /** 한 메시지의 사진을 정리한다(사진이 없으면 아무것도 안 함). 반드시 트랜잭션 안에서 호출한다. */
    public void purgeMessagePhoto(Long messageId) {
        String photoUrl = chatPhotoRepository.findPhotoUrlByMessageId(messageId);
        if (photoUrl == null) {
            return;
        }
        chatPhotoRepository.deleteByMessageId(messageId);   // DB 삭제는 이 트랜잭션에서
        scheduleS3Delete(List.of(photoUrl));
    }

    /** 커밋이 성공한 뒤에만 S3에서 지우도록 예약한다. */
    private void scheduleS3Delete(List<String> photoUrls) {
        if (photoUrls.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteFromS3(photoUrls);
                }
            });
        } else {
            // 트랜잭션이 없는 예외적 호출이면 즉시 삭제(방어적).
            deleteFromS3(photoUrls);
        }
    }

    private void deleteFromS3(List<String> photoUrls) {
        for (String url : photoUrls) {
            try {
                s3Uploader.delete(url);
            } catch (Exception e) {
                log.warn("S3 사진 삭제 실패(고아 객체가 남을 수 있음). url={}", url, e);
            }
        }
    }
}
