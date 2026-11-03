-- T223 / V3: session carts and checkout snapshots.
--
-- Carts live in the database, not in the HTTP session: a guest cart is located by a session
-- binding hash, an account cart by its owner and survives restarts, session expiry and logout
-- (Q08). The raw session id is never stored. A cart item is unique per (cart_id, itemid), so
-- there is exactly one line collection - the legacy list/index duality (D04a) cannot reappear.
--
-- Only one ACTIVE cart may exist per account. A generated column carries the owner for active
-- carts only, so a unique index on it allows consumed history carts to coexist while preventing
-- two active carts for the same account.
--
-- A checkout snapshot stores the server side prices, the cart version and both addresses with a
-- 15 minute expiry (Q03). It neither locks prices nor reserves stock.

CREATE TABLE cart (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    session_binding_hash CHAR(64)    NULL,
    owner_userid        VARCHAR(25)  NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    state               VARCHAR(16)  NOT NULL,
    expires_at          DATETIME(6)  NULL,
    created_at          DATETIME(6)  NOT NULL,
    active_owner        VARCHAR(25) GENERATED ALWAYS AS
        (CASE WHEN state = 'ACTIVE' THEN owner_userid ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_active_owner (active_owner),
    KEY idx_cart_session (session_binding_hash),
    KEY idx_cart_owner (owner_userid),
    KEY idx_cart_expiry (expires_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE cart_item (
    cart_id  BIGINT      NOT NULL,
    itemid   VARCHAR(10) NOT NULL,
    quantity INT         NOT NULL,
    PRIMARY KEY (cart_id, itemid),
    CONSTRAINT fk_cart_item_cart FOREIGN KEY (cart_id) REFERENCES cart (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE checkout (
    checkout_id      VARCHAR(128)   NOT NULL,
    user_id          VARCHAR(25)    NOT NULL,
    cart_id          BIGINT         NOT NULL,
    cart_version     BIGINT         NOT NULL,
    state            VARCHAR(16)    NOT NULL,
    created_at       DATETIME(6)    NOT NULL,
    expires_at       DATETIME(6)    NOT NULL,
    total_amount     DECIMAL(10, 2) NOT NULL,
    bill_first_name  VARCHAR(80)    NOT NULL,
    bill_last_name   VARCHAR(80)    NOT NULL,
    bill_addr1       VARCHAR(80)    NOT NULL,
    bill_addr2       VARCHAR(40)    NULL,
    bill_city        VARCHAR(80)    NOT NULL,
    bill_state       VARCHAR(80)    NOT NULL,
    bill_zip         VARCHAR(20)    NOT NULL,
    bill_country     VARCHAR(20)    NOT NULL,
    ship_first_name  VARCHAR(80)    NOT NULL,
    ship_last_name   VARCHAR(80)    NOT NULL,
    ship_addr1       VARCHAR(80)    NOT NULL,
    ship_addr2       VARCHAR(40)    NULL,
    ship_city        VARCHAR(80)    NOT NULL,
    ship_state       VARCHAR(80)    NOT NULL,
    ship_zip         VARCHAR(20)    NOT NULL,
    ship_country     VARCHAR(20)    NOT NULL,
    card_type        VARCHAR(20)    NOT NULL,
    card_last_four   CHAR(4)        NOT NULL,
    PRIMARY KEY (checkout_id),
    KEY idx_checkout_user (user_id),
    KEY idx_checkout_expiry (expires_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE checkout_item (
    checkout_id VARCHAR(128)   NOT NULL,
    linenum     INT            NOT NULL,
    itemid      VARCHAR(10)    NOT NULL,
    quantity    INT            NOT NULL,
    unit_price  DECIMAL(10, 2) NOT NULL,
    PRIMARY KEY (checkout_id, linenum),
    CONSTRAINT fk_checkout_item_checkout FOREIGN KEY (checkout_id) REFERENCES checkout (checkout_id)
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
