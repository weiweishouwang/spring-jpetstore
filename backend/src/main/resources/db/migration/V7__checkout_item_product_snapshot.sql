-- T462 / V7: keep the confirmed product text in the checkout snapshot.
--
-- A checkout snapshot is the content the user confirmed (Q03). The order must show exactly that
-- content, and it must still be creatable when the product is deleted or renamed afterwards
-- (Q15, ORD-19, D05). The snapshot therefore has to carry the product name and the attributes
-- instead of re-reading them from the catalog at order time.
--
-- Existing rows keep an empty product name: the column is only meaningful for snapshots created
-- after this migration, and the historical orders already store their own text snapshot.

ALTER TABLE checkout_item
    ADD COLUMN product_name VARCHAR(80) NOT NULL DEFAULT '',
    ADD COLUMN item_attribute VARCHAR(255) NULL;
