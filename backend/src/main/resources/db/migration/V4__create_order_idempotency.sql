-- T225 / V4: transactional idempotency records and the order/checkout relation.
--
-- A repeated submission must create at most one order and one stock deduction (NFR-03). The
-- idempotency record is written in the same transaction as the order, so a failed attempt rolls
-- the placeholder back with it. The stored response is replayable and deliberately carries no
-- cookie, CSRF token, password or full card number (plan 5.7).
--
-- A checkout snapshot may only ever produce one order; historical orders keep a NULL
-- checkout_id, and MySQL allows repeated NULLs in a unique index.

CREATE TABLE order_request (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    user_id           VARCHAR(25)   NOT NULL,
    operation         VARCHAR(64)   NOT NULL,
    idempotency_key   VARCHAR(128)  NOT NULL,
    request_hash      CHAR(64)      NOT NULL,
    order_id          BIGINT        NULL,
    response_status   INT           NULL,
    response_location VARCHAR(255)  NULL,
    response_body     MEDIUMTEXT    NULL,
    created_at        DATETIME(6)   NOT NULL,
    expires_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_request (user_id, operation, idempotency_key),
    KEY idx_order_request_expiry (expires_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

ALTER TABLE orders
    ADD UNIQUE KEY uk_orders_checkout (checkout_id);
