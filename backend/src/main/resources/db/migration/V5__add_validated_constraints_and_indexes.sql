-- T227 / V5: validated constraints and query indexes.
--
-- These constraints are only safe because the data was cleaned first: the legacy application
-- allowed negative stock, zero prices and out-of-range quantities (D02, D06). Adding them on
-- dirty data would make the migration unrecoverable, so the check constraints are introduced
-- here, after the tables exist and after the data has been validated.
--
--   Q02  inventory may never become negative (no overselling)
--   Q15  traded quantities are positive
--   Q06  prices are greater than zero
--   PER  the favourite category is one of the five known categories
--
-- The indexes back the documented query paths: category listing, product name search and the
-- order history of a user.

ALTER TABLE inventory
    ADD CONSTRAINT chk_inventory_qty CHECK (qty >= 0);

ALTER TABLE lineitem
    ADD CONSTRAINT chk_lineitem_quantity CHECK (quantity > 0);

ALTER TABLE cart_item
    ADD CONSTRAINT chk_cart_item_quantity CHECK (quantity > 0);

ALTER TABLE checkout_item
    ADD CONSTRAINT chk_checkout_item_quantity CHECK (quantity > 0);

ALTER TABLE item
    ADD CONSTRAINT chk_item_list_price CHECK (list_price > 0);

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_total_amount CHECK (total_amount >= 0);

ALTER TABLE profile
    ADD CONSTRAINT chk_profile_favourite_category
        CHECK (favourite_category IN ('FISH', 'DOGS', 'REPTILES', 'CATS', 'BIRDS'));

ALTER TABLE profile
    ADD CONSTRAINT chk_profile_language CHECK (language_preference IN ('english', 'japanese'));

CREATE INDEX idx_product_category ON product (category);
CREATE INDEX idx_product_name ON product (name);
CREATE INDEX idx_item_product ON item (productid);
CREATE INDEX idx_lineitem_item ON lineitem (itemid);
