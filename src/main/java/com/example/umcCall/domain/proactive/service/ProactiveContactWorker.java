package com.example.umcCall.domain.proactive.service;

import com.example.umcCall.domain.proactive.repository.ProactiveContactScheduleRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProactiveContactWorker {

    private final ProactiveContactScheduleRepository scheduleRepository;
    private final ProactiveContactProcessor processor;

    @Scheduled(fixedDelayString = "${proactive.scheduler-delay-ms:60000}")
    public void processDueContacts() {
        LocalDateTime now = LocalDateTime.now();
        scheduleRepository.findDueIds(now, PageRequest.of(0, 50)).forEach(scheduleId -> {
            ProactiveContactProcessor.Claim claim = processor.claim(scheduleId, now);
            if (claim == null) return;
            try {
                String reply = processor.generate(claim);
                if (reply != null) {
                    processor.complete(claim, reply, LocalDateTime.now());
                }
            } catch (RuntimeException exception) {
                processor.fail(claim, exception, LocalDateTime.now());
                log.error("선제 연락 처리 실패. scheduleId={}, requestId={}",
                        claim.scheduleId(), claim.requestId(), exception);
            }
        });
    }
}
