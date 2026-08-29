CREATE TABLE shopify_store_connection (
    id BIGINT NOT NULL AUTO_INCREMENT,
    shopify_shop_id VARCHAR(100) NOT NULL,
    myshopify_domain VARCHAR(255) NOT NULL,
    shop_name VARCHAR(255) NOT NULL,
    primary_domain VARCHAR(255) NULL,
    currency_code VARCHAR(3) NOT NULL,
    authorization_status VARCHAR(32) NOT NULL,
    connected_at DATETIME(6) NOT NULL,
    last_verified_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_shopify_store_connection_shop_id UNIQUE (shopify_shop_id),
    CONSTRAINT uk_shopify_store_connection_domain UNIQUE (myshopify_domain)
);
