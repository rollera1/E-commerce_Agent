CREATE TABLE product_selection (
    selection_id VARCHAR(36) NOT NULL,
    query_text VARCHAR(100) NOT NULL,
    matched_query VARCHAR(100) NOT NULL,
    candidate_product_ids TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    selected_source_product_id VARCHAR(200) NULL,
    created_at DATETIME(6) NOT NULL,
    confirmed_at DATETIME(6) NULL,
    PRIMARY KEY (selection_id),
    INDEX idx_product_selection_status (status),
    INDEX idx_product_selection_selected_product (selected_source_product_id)
);
