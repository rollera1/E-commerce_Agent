ALTER TABLE product_selection ADD COLUMN search_id VARCHAR(36) NULL;
CREATE UNIQUE INDEX uk_selection_search_product ON product_selection (search_id, selected_source_product_id);
