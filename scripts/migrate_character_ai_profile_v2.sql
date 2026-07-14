-- Hibernate ddl-auto=update는 제거된 컬럼을 drop하지 않으므로 배포 DB에서 한 번 실행한다.
ALTER TABLE character_ai_profile
    DROP COLUMN mind,
    DROP COLUMN response_style,
    DROP COLUMN romance_style_score,
    MODIFY COLUMN humor DOUBLE NOT NULL,
    MODIFY COLUMN playfulness DOUBLE NOT NULL,
    MODIFY COLUMN affection DOUBLE NOT NULL,
    MODIFY COLUMN empathy DOUBLE NOT NULL,
    MODIFY COLUMN attachment DOUBLE NOT NULL,
    MODIFY COLUMN jealousy DOUBLE NOT NULL,
    MODIFY COLUMN dominance DOUBLE NOT NULL,
    MODIFY COLUMN confidence DOUBLE NOT NULL,
    MODIFY COLUMN expressiveness DOUBLE NOT NULL,
    MODIFY COLUMN emotional_stability DOUBLE NOT NULL;

-- 값 backfill은 애플리케이션 시작 시 calculation_version < 2인 프로필에 대해 자동 수행된다.
