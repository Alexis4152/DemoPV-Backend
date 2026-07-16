-- ============================================================
--  Boutique POS — PostgreSQL schema
--  DB: safety_bmw_test  |  host: localhost
--  Run: psql -U postgres -d safety_bmw_test -f init.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    role        VARCHAR(20)  NOT NULL DEFAULT 'CASHIER'
                    CHECK (role IN ('ADMIN','CASHIER','SELLER')),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS products (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    barcode     VARCHAR(100),
    price       NUMERIC(12,2) NOT NULL DEFAULT 0,
    cost        NUMERIC(12,2) NOT NULL DEFAULT 0,
    stock       INTEGER NOT NULL DEFAULT 0,
    min_stock   INTEGER NOT NULL DEFAULT 0,
    unit        VARCHAR(20) NOT NULL DEFAULT 'pza',
    category_id BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS cash_cuts (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    opening_amount      NUMERIC(12,2) NOT NULL DEFAULT 0,
    closing_amount      NUMERIC(12,2),
    expenses            NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_sales         NUMERIC(12,2) NOT NULL DEFAULT 0,
    cash_sales          NUMERIC(12,2) NOT NULL DEFAULT 0,
    card_sales          NUMERIC(12,2) NOT NULL DEFAULT 0,
    transfer_sales      NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_transactions  INTEGER NOT NULL DEFAULT 0,
    status              VARCHAR(10) NOT NULL DEFAULT 'OPEN'
                            CHECK (status IN ('OPEN','CLOSED')),
    notes               TEXT,
    opened_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    closed_at           TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sales (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    cash_cut_id     BIGINT REFERENCES cash_cuts(id),
    customer_name   VARCHAR(150),
    subtotal        NUMERIC(12,2) NOT NULL DEFAULT 0,
    discount        NUMERIC(12,2) NOT NULL DEFAULT 0,
    tax             NUMERIC(12,2) NOT NULL DEFAULT 0,
    total           NUMERIC(12,2) NOT NULL DEFAULT 0,
    payment_method  VARCHAR(20) NOT NULL DEFAULT 'CASH'
                        CHECK (payment_method IN ('CASH','CARD','TRANSFER')),
    status          VARCHAR(15) NOT NULL DEFAULT 'COMPLETED'
                        CHECK (status IN ('COMPLETED','CANCELLED')),
    notes           TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS sale_items (
    id              BIGSERIAL PRIMARY KEY,
    sale_id         BIGINT NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    product_id      BIGINT REFERENCES products(id) ON DELETE SET NULL,
    product_name    VARCHAR(200) NOT NULL,
    quantity        NUMERIC(10,3) NOT NULL DEFAULT 1,
    unit_price      NUMERIC(12,2) NOT NULL DEFAULT 0,
    discount        NUMERIC(12,2) NOT NULL DEFAULT 0,
    subtotal        NUMERIC(12,2) NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS inventory_movements (
    id              BIGSERIAL PRIMARY KEY,
    product_id      BIGINT NOT NULL REFERENCES products(id),
    user_id         BIGINT NOT NULL REFERENCES users(id),
    type            VARCHAR(15) NOT NULL
                        CHECK (type IN ('IN','OUT','ADJUSTMENT','SALE')),
    quantity        INTEGER NOT NULL,
    previous_stock  INTEGER NOT NULL,
    new_stock       INTEGER NOT NULL,
    reason          VARCHAR(255),
    reference       VARCHAR(100),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_products_category   ON products(category_id);
CREATE INDEX IF NOT EXISTS idx_products_barcode    ON products(barcode);
CREATE INDEX IF NOT EXISTS idx_sales_user          ON sales(user_id);
CREATE INDEX IF NOT EXISTS idx_sales_created_at    ON sales(created_at);
CREATE INDEX IF NOT EXISTS idx_sale_items_sale     ON sale_items(sale_id);
CREATE INDEX IF NOT EXISTS idx_inv_movements_prod  ON inventory_movements(product_id);
CREATE INDEX IF NOT EXISTS idx_cash_cuts_status    ON cash_cuts(status);

-- Default admin (password: admin123)
INSERT INTO users (name, email, password, role)
SELECT 'Administrador','admin@boutique.com',
       '$2b$10$PMm3XPaFv7Rm150MI4NP2uFHtyQ6Sxh1UDBGwcaSp9v7Cn3Ikn/ou','ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email='admin@boutique.com');

-- Default categories
INSERT INTO categories (name, description)
SELECT * FROM (VALUES
  ('General',     'Categoría general'),
  ('Ropa',        'Prendas de vestir'),
  ('Accesorios',  'Bolsos, cinturones y complementos'),
  ('Calzado',     'Zapatos y tenis')
) AS v(n,d)
WHERE NOT EXISTS (SELECT 1 FROM categories LIMIT 1);
