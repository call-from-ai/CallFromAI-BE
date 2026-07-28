-- 예약 통화(call_reservation) 제거. AI 발신은 proactive 스케줄러가 대신한다.
-- ddl-auto: update 는 컬럼·테이블을 지우지 않으므로 수동으로 정리한다.
-- ⚠ 실행 순서 주의: FK를 먼저 끊어야 테이블을 지울 수 있다.

-- 1) calls -> call_reservation FK 제약 이름 확인
--    SELECT constraint_name FROM information_schema.key_column_usage
--     WHERE table_schema = DATABASE() AND table_name = 'calls'
--       AND column_name = 'call_reservation_id';
-- 2) 확인한 이름으로 제약 삭제 (하이버네이트가 만든 FK 이름은 환경마다 다르다)
--    ALTER TABLE calls DROP FOREIGN KEY <constraint_name>;

ALTER TABLE calls DROP COLUMN call_reservation_id;

DROP TABLE IF EXISTS call_reservation;
