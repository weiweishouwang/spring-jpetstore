-- T174: deterministic business test data for the modern test database.
--
-- Loaded into the isolated Testcontainers schema (MySqlTestDatabase) AFTER the Flyway
-- migrations of Phase 4 have run; never imported into any production environment.
--
-- Column names follow the Phase 4 migration drafts (docs/plan.md 7.2); if V1~V5 settle on
-- different names, update this fixture in a dedicated task instead of editing migrations.
--
-- Coverage required by AT scenarios:
--   * two users: alice (banner data MISSING for her favourite category), bob (banner exists)
--   * multiple SKUs per product with distinct prices (cart merge / money tests)
--   * low stock (EST-20 qty=1) and zero stock (EST-21 qty=0) items
--   * one historical order with priced line-item snapshots for bob (D05/AT-R04)
--
-- Test credentials (BCrypt, cost 10, generated and verified 2026-11-03):
--   alice / alice-pass-123
--   bob   / bob-pass-456

-- Accounts, credentials, preferences -------------------------------------------

INSERT INTO signon (username, password_hash) VALUES
  ('alice', '$2a$10$KoKo90IH4cyuihN.Pw07OucBJniWznqr8mpSyAMW7iftUyHG3x8y.'),
  ('bob',   '$2a$10$V9RNpz5LsFmC5WWpbDa71ebakOyx6t5VaSSxJxIpm9EtCT43H2laW');

INSERT INTO account (userid, email, first_name, last_name, addr1, addr2, city, state, zip, country, phone) VALUES
  ('alice', 'alice@example.com', 'Alice', 'Ishikawa', '1-2-3 Sakuragaoka', NULL, 'Shibuya', 'Tokyo', '150-0001', 'JP', '090-1111-2222'),
  ('bob',   'bob@example.com',   'Bob',   'Yamada',   '4-5-6 Omotesando',  'Apt 301', 'Minato', 'Tokyo', '107-0052', 'JP', '090-3333-4444');

INSERT INTO profile (userid, language_preference, favourite_category, list_option, banner_option) VALUES
  -- alice favours BIRDS, for which no bannerdata row exists (missing-banner scenario)
  ('alice', 'english', 'BIRDS', TRUE, TRUE),
  ('bob',   'english', 'FISH',  TRUE, TRUE);

-- Banner data: only four categories, BIRDS intentionally absent ------------------

INSERT INTO bannerdata (favcategory, banner_name) VALUES
  ('FISH',     'fish_banner'),
  ('DOGS',     'dogs_banner'),
  ('REPTILES', 'reptiles_banner'),
  ('CATS',     'cats_banner');

-- Catalog ------------------------------------------------------------------------

INSERT INTO category (catid, name, description) VALUES
  ('FISH',     'Fish',     'Saltwater and freshwater friends'),
  ('DOGS',     'Dogs',     'Various sizes of man''s best friend'),
  ('REPTILES', 'Reptiles', 'Lizards, turtles and snakes'),
  ('CATS',     'Cats',     'Felines of all persuasions'),
  ('BIRDS',    'Birds',    'Feathered companions');

-- Suppliers (referenced by the items below) ---------------------------------------------

INSERT INTO supplier (suppid, name, status, addr1, addr2, city, state, zip, country, phone) VALUES
  (1, 'Pacific Wholesale', 'P', '1 Harbour Way', NULL, 'Sydney', 'NSW', '2000', 'AU', '02-5555-0100'),
  (2, 'East Coast Supply', 'P', '2 Riverside Road', 'Warehouse 4', 'Boston', 'MA', '02108', 'US', '617-555-0180');

INSERT INTO product (productid, category, name, description) VALUES  ('FI-SW-01', 'FISH',     'Angelfish',  'Saltwater fish from Australia'),
  ('FI-SW-02', 'FISH',     'Tiger Shark','Saltwater fish from Australia'),
  ('FI-FW-01', 'FISH',     'Koi',        'Freshwater fish from Japan'),
  ('FI-FW-02', 'FISH',     'Goldfish',   'Freshwater fish from China'),
  ('K9-BD-01', 'DOGS',     'Bulldog',    'Friendly dog from England'),
  ('K9-PO-02', 'DOGS',     'Poodle',     'Cute dog from France'),
  ('RP-SN-01', 'REPTILES', 'Rattlesnake','Dangerous snake from the Americas'),
  ('RP-LI-01', 'REPTILES', 'Iguana',     'Friendly lizard from Mexico'),
  ('FL-DSH-01','CATS',     'Persian',    'Long-haired cat from Persia'),
  ('AV-CB-01', 'BIRDS',    'Amazon Parrot','Long-living parrot from the Amazon'),
  ('AV-SB-02', 'BIRDS',    'Finch',      'Chirping songbird');

INSERT INTO item (itemid, productid, list_price, unit_cost, supplier, status, attr1, attr2) VALUES
  ('EST-1',  'FI-SW-01', 16.50, 10.00, 1, 'P', 'Large',  'Adult male'),
  ('EST-2',  'FI-SW-01', 10.50, 6.00,  1, 'P', 'Small',  'Adult female'),
  ('EST-3',  'FI-SW-02', 18.50, 12.00, 1, 'P', 'Toothless', 'Juvenile'),
  ('EST-4',  'FI-FW-01', 12.00, 7.50,  1, 'P', 'Spotted', 'Adult'),
  ('EST-5',  'FI-FW-02',  5.50, 2.00,  1, 'P', 'Standard', 'Young'),
  ('EST-6',  'FI-FW-02',  7.50, 3.00,  1, 'P', 'Fancy',   'Young'),
  ('EST-10', 'K9-BD-01', 25.50, 15.00, 2, 'P', 'Spotless', 'Adult male'),
  ('EST-11', 'K9-BD-01', 22.50, 13.00, 2, 'P', 'Spotted',  'Adult female'),
  ('EST-12', 'K9-PO-02', 30.50, 18.00, 2, 'P', 'Standard', 'Adult'),
  ('EST-20', 'RP-SN-01', 15.50, 9.00,  2, 'P', 'Venomless','Adult'),
  ('EST-21', 'RP-LI-01',  9.50, 5.00,  2, 'P', 'Green',    'Juvenile'),
  ('EST-30', 'FL-DSH-01',28.50, 17.00, 2, 'P', 'White',    'Adult female'),
  ('EST-40', 'AV-CB-01', 120.00, 85.00, 2, 'P', 'Talking', 'Adult'),
  ('EST-41', 'AV-SB-02',  5.00,  2.50,  2, 'P', 'Yellow',  'Juvenile');

-- Inventory: normal stock, low stock (EST-20 qty=1), zero stock (EST-21) ------------

INSERT INTO inventory (itemid, qty, version) VALUES
  ('EST-1', 10000, 0), ('EST-2', 10000, 0), ('EST-3', 10000, 0), ('EST-4', 10000, 0),
  ('EST-5', 10000, 0), ('EST-6', 10000, 0), ('EST-10', 10000, 0), ('EST-11', 10000, 0),
  ('EST-12', 10000, 0), ('EST-20', 1, 0), ('EST-21', 0, 0), ('EST-30', 10000, 0),
  ('EST-40', 10000, 0), ('EST-41', 10000, 0);

-- Historical order with priced snapshots (bob; D05 / AT-R04) ----------------------
-- Two Angelfish (EST-1) bought at the historical unit price 10.00 (current list 16.50):
-- historical amounts must NOT follow the current price.

INSERT INTO orders (orderid, userid, orderdate, bill_addr1, bill_addr2, bill_city, bill_state,
                    bill_zip, bill_country, ship_addr1, ship_addr2, ship_city, ship_state,
                    ship_zip, ship_country, total_amount, status)
VALUES (1000, 'bob', '2026-10-01 10:00:00', '4-5-6 Omotesando', 'Apt 301', 'Minato', 'Tokyo',
        '107-0052', 'JP', '4-5-6 Omotesando', 'Apt 301', 'Minato', 'Tokyo',
        '107-0052', 'JP', 20.00, 'P');

INSERT INTO orderstatus (orderid, linenum, status, timestamp) VALUES
  (1000, 1000, 'P', '2026-10-01 10:00:00');

INSERT INTO lineitem (orderid, linenum, itemid, quantity, unit_price, product_name, item_attribute)
VALUES
  (1000, 1, 'EST-1', 2, 10.00, 'Angelfish', 'Large / Adult male');
