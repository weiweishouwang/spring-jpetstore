-- T219 / V1: account, credentials, preferences and catalog.
--
-- Identifier and comparison rules follow the confirmed decisions:
--   Q13  username is trimmed, 1-25 characters and case sensitive (utf8mb4_bin)
--   NFR-04 password is stored as a hash only; the column allows 255 characters
--   Q01  address second line is optional
--   PER  language preference and favourite category are stored as given
--
-- The legacy account/signon/profile tables had no foreign keys between them; the logical
-- user-id relation is preserved without adding constraints that the legacy data never had.

CREATE TABLE signon (
    username     VARCHAR(25)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    PRIMARY KEY (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE account (
    userid     VARCHAR(25)  NOT NULL,
    email      VARCHAR(80)  NOT NULL,
    first_name VARCHAR(80)  NOT NULL,
    last_name  VARCHAR(80)  NOT NULL,
    phone      VARCHAR(80)  NOT NULL,
    addr1      VARCHAR(80)  NOT NULL,
    addr2      VARCHAR(40)  NULL,
    city       VARCHAR(80)  NOT NULL,
    state      VARCHAR(80)  NOT NULL,
    zip        VARCHAR(20)  NOT NULL,
    country    VARCHAR(20)  NOT NULL,
    PRIMARY KEY (userid)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE profile (
    userid             VARCHAR(25) NOT NULL,
    language_preference VARCHAR(80) NOT NULL,
    favourite_category VARCHAR(30) NOT NULL,
    list_option        BOOLEAN     NOT NULL DEFAULT TRUE,
    banner_option      BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (userid)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

-- Banner content is a controlled image reference plus plain text (D07/NFR-05).
-- Rows may be missing: the account query must not depend on this table (PER-03).
CREATE TABLE bannerdata (
    favcategory VARCHAR(30)  NOT NULL,
    banner_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (favcategory)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE category (
    catid       VARCHAR(10)  NOT NULL,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(255) NOT NULL,
    PRIMARY KEY (catid)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE supplier (
    suppid   INT         NOT NULL AUTO_INCREMENT,
    name     VARCHAR(80) NOT NULL,
    status   VARCHAR(2)  NOT NULL,
    addr1    VARCHAR(80) NOT NULL,
    addr2    VARCHAR(80) NULL,
    city     VARCHAR(80) NOT NULL,
    state    VARCHAR(80) NOT NULL,
    zip      VARCHAR(20) NOT NULL,
    country  VARCHAR(20) NOT NULL,
    phone    VARCHAR(20) NOT NULL,
    PRIMARY KEY (suppid)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE product (
    productid  VARCHAR(10)  NOT NULL,
    category   VARCHAR(10)  NOT NULL,
    name       VARCHAR(80)  NOT NULL,
    description VARCHAR(255) NOT NULL,
    PRIMARY KEY (productid),
    CONSTRAINT fk_product_category FOREIGN KEY (category) REFERENCES category (catid)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE item (
    itemid     VARCHAR(10)   NOT NULL,
    productid  VARCHAR(10)   NOT NULL,
    list_price DECIMAL(10, 2) NOT NULL,
    unit_cost  DECIMAL(10, 2) NOT NULL,
    supplier   INT           NOT NULL,
    status     VARCHAR(2)    NOT NULL,
    attr1      VARCHAR(80)   NULL,
    attr2      VARCHAR(80)   NULL,
    attr3      VARCHAR(80)   NULL,
    attr4      VARCHAR(80)   NULL,
    attr5      VARCHAR(80)   NULL,
    PRIMARY KEY (itemid),
    CONSTRAINT fk_item_product FOREIGN KEY (productid) REFERENCES product (productid),
    CONSTRAINT fk_item_supplier FOREIGN KEY (supplier) REFERENCES supplier (suppid)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
