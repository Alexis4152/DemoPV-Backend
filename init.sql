-- ============================================================
--  Boutique POS — PostgreSQL schema
--  DB: safety_bmw_test  |  host: localhost
--  Run: psql -U postgres -d safety_bmw_test -f init.sql
-- ============================================================

-- Tiendas dadas de alta en la plataforma (módulo /api/tiendas)
CREATE TABLE IF NOT EXISTS tiendas (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    tienda_id   BIGINT REFERENCES tiendas(id),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    tienda_id   BIGINT REFERENCES tiendas(id),
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
    tienda_id   BIGINT REFERENCES tiendas(id),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS cash_cuts (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    tienda_id           BIGINT REFERENCES tiendas(id),
    opening_amount      NUMERIC(12,2) NOT NULL DEFAULT 0,
    closing_amount      NUMERIC(12,2),
    expenses            NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_sales         NUMERIC(12,2) NOT NULL DEFAULT 0,
    cash_sales          NUMERIC(12,2) NOT NULL DEFAULT 0,
    card_sales          NUMERIC(12,2) NOT NULL DEFAULT 0,
    transfer_sales      NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_transactions  INTEGER NOT NULL DEFAULT 0,
    cancelled_count     INTEGER NOT NULL DEFAULT 0,
    cancelled_total     NUMERIC(12,2) NOT NULL DEFAULT 0,
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
    tienda_id       BIGINT REFERENCES tiendas(id),
    customer_name   VARCHAR(150),
    customer_email  VARCHAR(150),
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
CREATE INDEX IF NOT EXISTS idx_products_tienda     ON products(tienda_id);
CREATE INDEX IF NOT EXISTS idx_categories_tienda   ON categories(tienda_id);
CREATE INDEX IF NOT EXISTS idx_sales_user          ON sales(user_id);
CREATE INDEX IF NOT EXISTS idx_sales_created_at    ON sales(created_at);
CREATE INDEX IF NOT EXISTS idx_sales_tienda        ON sales(tienda_id);
CREATE INDEX IF NOT EXISTS idx_sale_items_sale     ON sale_items(sale_id);
CREATE INDEX IF NOT EXISTS idx_inv_movements_prod  ON inventory_movements(product_id);
CREATE INDEX IF NOT EXISTS idx_cash_cuts_status    ON cash_cuts(status);
CREATE INDEX IF NOT EXISTS idx_cash_cuts_tienda    ON cash_cuts(tienda_id);
CREATE INDEX IF NOT EXISTS idx_users_tienda        ON users(tienda_id);

-- Tienda por defecto para el primer arranque
INSERT INTO tiendas (name)
SELECT 'Tienda Principal'
WHERE NOT EXISTS (SELECT 1 FROM tiendas LIMIT 1);

-- Default admin (password: admin123). El rol (role_id) se lo asignan RoleDataInitializer +
-- TenantDataInitializer al arrancar la app: siembran ADMIN/CASHIER/SELLER (uno por tienda,
-- nunca compartidos entre tiendas) y le dan el ADMIN de su tienda a este usuario. La tabla
-- roles la administra Hibernate, no este script.
INSERT INTO users (name, email, password, tienda_id)
SELECT 'Administrador','admin@boutique.com',
       '$2b$10$PMm3XPaFv7Rm150MI4NP2uFHtyQ6Sxh1UDBGwcaSp9v7Cn3Ikn/ou',
       (SELECT id FROM tiendas ORDER BY id LIMIT 1)
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email='admin@boutique.com');

-- Default categories
INSERT INTO categories (name, description, tienda_id)
SELECT v.n, v.d, (SELECT id FROM tiendas ORDER BY id LIMIT 1)
FROM (VALUES
  ('General',     'Categoría general'),
  ('Ropa',        'Prendas de vestir'),
  ('Accesorios',  'Bolsos, cinturones y complementos'),
  ('Calzado',     'Zapatos y tenis')
) AS v(n,d)
WHERE NOT EXISTS (SELECT 1 FROM categories LIMIT 1);
