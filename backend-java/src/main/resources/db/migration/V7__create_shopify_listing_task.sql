CREATE TABLE shopify_listing_task (
    generation_id VARCHAR(36) NOT NULL,
    source_product_id VARCHAR(200) NOT NULL,
    review_mode VARCHAR(16) NOT NULL,
    publish_mode VARCHAR(16) NOT NULL,
    location_id VARCHAR(100) NOT NULL,
    publication_id VARCHAR(100) NOT NULL,
    auto_publish_authorized BOOLEAN NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_step VARCHAR(32) NOT NULL,
    shopify_product_id VARCHAR(100) NULL,
    image_urls TEXT NOT NULL,
    media_ready BOOLEAN NOT NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (generation_id),
    UNIQUE KEY uk_shopify_listing_product (shopify_product_id),
    CONSTRAINT fk_listing_generation FOREIGN KEY (generation_id)
        REFERENCES copy_seo_generation (generation_id)
);

CREATE TABLE shopify_listing_variant (
    id BIGINT NOT NULL AUTO_INCREMENT,
    generation_id VARCHAR(36) NOT NULL,
    source_variant_id VARCHAR(200) NOT NULL,
    option_value VARCHAR(255) NOT NULL,
    sku VARCHAR(255) NOT NULL,
    selling_price DECIMAL(19, 4) NOT NULL,
    source_inventory INT NOT NULL,
    shopify_variant_id VARCHAR(100) NULL,
    shopify_inventory_item_id VARCHAR(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_listing_source_variant (generation_id, source_variant_id),
    CONSTRAINT fk_listing_variant_task FOREIGN KEY (generation_id)
        REFERENCES shopify_listing_task (generation_id)
);
