-- Run only against a dedicated PostgreSQL test database after Flyway migration.
-- Synthetic rows are rolled back; identity sequences may still advance.
\set ON_ERROR_STOP on
BEGIN;
INSERT INTO app_user(username, password_hash, role)
VALUES ('explain_demo_user', 'synthetic-not-a-login-hash', 'USER');

INSERT INTO product(sku, name, description, category, price, active)
SELECT 'EXPLAIN-SKU-' || g, 'Synthetic product ' || g, '',
       'category-' || (g % 20), 19.90, TRUE
FROM generate_series(1, 50000) AS g;

INSERT INTO shop_order(order_no, user_id, request_key, status, total_amount, created_at)
SELECT 'EXPLAIN-ORDER-' || g,
       (SELECT id FROM app_user WHERE username = 'explain_demo_user'),
       'EXPLAIN-KEY-' || g,
       CASE WHEN g % 2 = 0 THEN 'CREATED' ELSE 'CANCELLED' END,
       19.90, CURRENT_TIMESTAMP - g * INTERVAL '1 second'
FROM generate_series(1, 50000) AS g;

ANALYZE product;
ANALYZE shop_order;

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM product
WHERE active = TRUE AND category = 'category-5'
ORDER BY id DESC LIMIT 20;

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM shop_order
WHERE user_id = (SELECT id FROM app_user WHERE username = 'explain_demo_user')
  AND status = 'CREATED'
ORDER BY created_at DESC, id DESC LIMIT 20;
ROLLBACK;
