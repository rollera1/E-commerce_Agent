ALTER TABLE copy_seo_generation
    MODIFY title VARCHAR(255) NULL,
    MODIFY bullet_points TEXT NULL,
    MODIFY description TEXT NULL,
    MODIFY seo_title VARCHAR(70) NULL,
    MODIFY meta_description VARCHAR(160) NULL,
    MODIFY tags TEXT NULL,
    ADD COLUMN error_type VARCHAR(64) NULL AFTER duration_ms,
    ADD COLUMN retryable BOOLEAN NULL AFTER error_type;
