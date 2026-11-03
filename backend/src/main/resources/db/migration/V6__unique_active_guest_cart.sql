-- T459 / V6: unique active guest cart per session binding.
--
-- V3 only guaranteed one ACTIVE cart per account (uk_cart_active_owner). A guest cart is located
-- by its session binding hash, and two concurrent requests of the same guest session could both
-- observe "no cart" and create one each, leaving two active carts for the same session and making
-- the lookup ambiguous (Q08, CRT-01).
--
-- The generated column mirrors the account rule: it carries the binding hash of active carts only,
-- so consumed and expired history rows are exempt and repeated NULLs stay allowed in the unique
-- index.

ALTER TABLE cart
    ADD COLUMN active_session CHAR(64) GENERATED ALWAYS AS
        (CASE WHEN state = 'ACTIVE' THEN session_binding_hash ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_cart_active_session (active_session);
