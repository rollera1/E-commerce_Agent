CREATE TABLE operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operation_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    message VARCHAR(1000),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_operation_log_created_at (created_at),
    INDEX idx_operation_log_type_status (operation_type, status)
);
