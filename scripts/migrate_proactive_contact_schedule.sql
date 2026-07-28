CREATE TABLE IF NOT EXISTS proactive_contact_schedule (
    proactive_contact_schedule_id BIGINT NOT NULL AUTO_INCREMENT,
    relationship_id BIGINT NOT NULL,
    enabled BIT NOT NULL DEFAULT 1,
    next_check_at DATETIME(6) NULL,
    last_proactive_contact_at DATETIME(6) NULL,
    consecutive_no_response_count INT NOT NULL DEFAULT 0,
    awaiting_user_response BIT NOT NULL DEFAULT 0,
    daily_contact_count INT NOT NULL DEFAULT 0,
    daily_call_count INT NOT NULL DEFAULT 0,
    daily_count_date DATE NULL,
    paused_until DATETIME(6) NULL,
    pending_request_id VARCHAR(80) NULL,
    pending_action VARCHAR(20) NULL,
    pending_contact_reason VARCHAR(80) NULL,
    pending_attempts INT NOT NULL DEFAULT 0,
    pending_retry_at DATETIME(6) NULL,
    last_error VARCHAR(1000) NULL,
    version BIGINT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (proactive_contact_schedule_id),
    CONSTRAINT uk_proactive_relationship UNIQUE (relationship_id),
    CONSTRAINT uk_proactive_pending_request UNIQUE (pending_request_id),
    CONSTRAINT fk_proactive_relationship
        FOREIGN KEY (relationship_id) REFERENCES relationship (relationship_id)
);

CREATE INDEX idx_proactive_due
    ON proactive_contact_schedule (enabled, next_check_at);

CREATE INDEX idx_proactive_retry
    ON proactive_contact_schedule (pending_retry_at);

ALTER TABLE chat_message
    ADD COLUMN proactive_request_id VARCHAR(80) NULL;

CREATE UNIQUE INDEX uk_chat_message_proactive_request
    ON chat_message (proactive_request_id);

CREATE INDEX idx_calls_relationship_status_created
    ON calls (relationship_id, status, created_at);

-- 과거 코드에서 저장한 직업 enum을 현재 canonical code로 정규화한다.
UPDATE `character`
SET job = 'UNIVERSITY_STUDENT'
WHERE job = 'STUDENT';

INSERT INTO proactive_contact_schedule (
    relationship_id,
    enabled,
    next_check_at,
    consecutive_no_response_count,
    awaiting_user_response,
    daily_contact_count,
    daily_call_count,
    pending_attempts,
    version,
    created_at,
    updated_at
)
SELECT
    r.relationship_id,
    CASE WHEN c.deleted_at IS NULL THEN 1 ELSE 0 END,
    CASE
        WHEN c.deleted_at IS NULL THEN DATE_ADD(NOW(6), INTERVAL 2 HOUR)
        ELSE NULL
    END,
    0,
    0,
    0,
    0,
    0,
    0,
    NOW(6),
    NOW(6)
FROM relationship r
JOIN `character` c ON c.character_id = r.character_id
LEFT JOIN proactive_contact_schedule pcs ON pcs.relationship_id = r.relationship_id
WHERE pcs.proactive_contact_schedule_id IS NULL;
