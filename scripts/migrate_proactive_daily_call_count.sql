ALTER TABLE proactive_contact_schedule
    ADD COLUMN daily_call_count INT NOT NULL DEFAULT 0
    AFTER daily_contact_count;
