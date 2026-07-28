package com.example.umcCall.domain.proactive.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 스케줄 기능 도입 전에 생성된 기존 관계에도 스케줄 행을 보장한다.
 * DB migration을 적용하지 않고 ddl-auto=update로 실행하는 local 환경도 지원한다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProactiveScheduleBackfillRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        // Character 엔티티를 로딩하지 않는다. 레거시 enum 값 등 다른 도메인의 데이터가
        // 잘못돼 있어도 스케줄 backfill 때문에 애플리케이션 시작 전체가 실패하지 않게 한다.
        int created = jdbcTemplate.update("""
                INSERT INTO proactive_contact_schedule (
                    relationship_id, enabled, next_check_at,
                    consecutive_no_response_count, awaiting_user_response,
                    daily_contact_count, daily_call_count, pending_attempts, version,
                    created_at, updated_at
                )
                SELECT
                    r.relationship_id,
                    CASE WHEN c.deleted_at IS NULL THEN 1 ELSE 0 END,
                    CASE WHEN c.deleted_at IS NULL
                         THEN DATE_ADD(NOW(6), INTERVAL 2 HOUR)
                         ELSE NULL END,
                    0, 0, 0, 0, 0, 0, NOW(6), NOW(6)
                FROM relationship r
                JOIN `character` c ON c.character_id = r.character_id
                LEFT JOIN proactive_contact_schedule pcs
                       ON pcs.relationship_id = r.relationship_id
                WHERE c.deleted_at IS NULL
                  AND pcs.proactive_contact_schedule_id IS NULL
                """);
        int reactivated = jdbcTemplate.update("""
                UPDATE proactive_contact_schedule pcs
                JOIN relationship r ON r.relationship_id = pcs.relationship_id
                JOIN `character` c ON c.character_id = r.character_id
                SET pcs.enabled = 1,
                    pcs.next_check_at = COALESCE(
                        pcs.next_check_at,
                        DATE_ADD(NOW(6), INTERVAL 2 HOUR))
                WHERE c.deleted_at IS NULL
                  AND pcs.enabled = 0
                """);
        if (created > 0) {
            log.info("기존 관계 선제 연락 스케줄 backfill 완료. created={}", created);
        }
        if (reactivated > 0) {
            log.info("비메인 관계 선제 채팅 스케줄 활성화 완료. reactivated={}", reactivated);
        }
    }
}
