ALTER TABLE shopify_listing_task
    ADD COLUMN applied_image_urls TEXT NULL AFTER image_urls;

UPDATE shopify_listing_task
SET applied_image_urls = image_urls;

ALTER TABLE shopify_listing_task
    MODIFY COLUMN applied_image_urls TEXT NOT NULL;
