-- T221 / V2: orders, order status, line items and inventory.
--
-- Money is CNY with two decimals (Q06); the order total is the sum of the line amounts and
-- never includes hidden fees (ORD-15). Order ids are BIGINT auto increment so concurrent
-- creation cannot allocate the same number (ORD-09). Line items keep the traded price and the
-- product text as a snapshot: a later price change or product deletion must not alter a
-- historical order (ORD-19, Q15, D05). Timestamps are UTC with microsecond precision (Q11).
--
-- There is deliberately no foreign key from lineitem to item: a deleted product must not break
-- the display of historical orders.

CREATE TABLE inventory (
    itemid  VARCHAR(10) NOT NULL,
    qty     INT         NOT NULL,
    version BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (itemid),
    CONSTRAINT fk_inventory_item FOREIGN KEY (itemid) REFERENCES item (itemid)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE orders (
    orderid        BIGINT        NOT NULL AUTO_INCREMENT,
    userid         VARCHAR(25)   NOT NULL,
    orderdate      DATETIME(6)   NOT NULL,
    bill_first_name VARCHAR(80)  NOT NULL,
    bill_last_name  VARCHAR(80)  NOT NULL,
    bill_addr1      VARCHAR(80)  NOT NULL,
    bill_addr2      VARCHAR(40)  NULL,
    bill_city       VARCHAR(80)  NOT NULL,
    bill_state      VARCHAR(80)  NOT NULL,
    bill_zip        VARCHAR(20)  NOT NULL,
    bill_country    VARCHAR(20)  NOT NULL,
    ship_first_name VARCHAR(80)  NOT NULL,
    ship_last_name  VARCHAR(80)  NOT NULL,
    ship_addr1      VARCHAR(80)  NOT NULL,
    ship_addr2      VARCHAR(40)  NULL,
    ship_city       VARCHAR(80)  NOT NULL,
    ship_state      VARCHAR(80)  NOT NULL,
    ship_zip        VARCHAR(20)  NOT NULL,
    ship_country    VARCHAR(20)  NOT NULL,
    total_amount    DECIMAL(10, 2) NOT NULL,
    status          VARCHAR(2)    NOT NULL,
    courier         VARCHAR(40)  NULL,
    region          VARCHAR(40)  NULL,
    card_type       VARCHAR(20)  NOT NULL,
    card_last_four  CHAR(4)      NOT NULL,
    checkout_id     VARCHAR(128) NULL,
    PRIMARY KEY (orderid),
    KEY idx_orders_user_date (userid, orderdate, orderid)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE orderstatus (
    orderid   BIGINT      NOT NULL,
    linenum   INT         NOT NULL,
    status    VARCHAR(2)  NOT NULL,
    timestamp DATETIME(6) NOT NULL,
    PRIMARY KEY (orderid, linenum),
    CONSTRAINT fk_orderstatus_order FOREIGN KEY (orderid) REFERENCES orders (orderid)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE lineitem (
    orderid       BIGINT        NOT NULL,
    linenum       INT           NOT NULL,
    itemid        VARCHAR(10)   NOT NULL,
    quantity      INT           NOT NULL,
    unit_price    DECIMAL(10, 2) NOT NULL,
    product_name  VARCHAR(80)   NOT NULL,
    item_attribute VARCHAR(255) NULL,
    PRIMARY KEY (orderid, linenum),
    CONSTRAINT fk_lineitem_order FOREIGN KEY (orderid) REFERENCES orders (orderid)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

-- Kept only for compatibility with the legacy demo data; new orders use the auto increment
-- of the orders table and never read this table (plan 7.2).
CREATE TABLE sequence (
    name   VARCHAR(30) NOT NULL,
    nextid INT          NOT NULL,
    PRIMARY KEY (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
