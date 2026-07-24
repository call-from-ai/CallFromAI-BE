package com.example.umcCall.domain.proactive.service;

import com.example.umcCall.domain.proactive.repository.ProactiveContactScheduleRepository;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스케줄 기능 도입 전에 생성된 기존 관계에도 스케줄 행을 보장한다.
 * DB migration을 적용하지 않고 ddl-auto=update로 실행하는 local 환경도 지원한다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProactiveScheduleBackfillRunner implements ApplicationRunner {

    private final RelationshipRepository relationshipRepository;
    private final ProactiveContactScheduleRepository scheduleRepository;
    private final ProactiveScheduleCoordinator coordinator;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int created = 0;
        for (var relationship : relationshipRepository.findAll()) {
            if (relationship.getCharacter().getDeletedAt() != null
                    || scheduleRepository.findByRelationshipId(relationship.getId()).isPresent()) {
                continue;
            }
            coordinator.create(relationship);
            created++;
        }
        if (created > 0) {
            log.info("기존 관계 선제 연락 스케줄 backfill 완료. created={}", created);
        }
    }
}
