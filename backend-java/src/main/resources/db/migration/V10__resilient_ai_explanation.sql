ALTER TABLE ai_explanation
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN failure_code VARCHAR(80),
    ADD COLUMN duration_ms BIGINT,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE ai_explanation
SET attempt_count = 1,
    updated_at = created_at
WHERE attempt_count = 0;
