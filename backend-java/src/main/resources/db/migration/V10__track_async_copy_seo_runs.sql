ALTER TABLE copy_seo_generation
    MODIFY title VARCHAR(255) NULL,
    MODIFY bullet_points TEXT NULL,
    MODIFY description TEXT NULL,
    MODIFY seo_title VARCHAR(70) NULL,
    MODIFY meta_description VARCHAR(160) NULL,
    MODIFY tags TEXT NULL,
    ADD COLUMN agent_run_id VARCHAR(36) NULL,
    ADD COLUMN agent_run_attempt INT NOT NULL DEFAULT 0,
    ADD COLUMN next_poll_at DATETIME(6) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD UNIQUE KEY uk_copy_seo_generation_agent_run (agent_run_id),
    ADD INDEX idx_copy_seo_generation_poll (status, next_poll_at);
