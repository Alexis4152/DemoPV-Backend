-- ============================================================
--  Boutique POS — Migración: materia prima
--  DB: safety_bmw_test  |  host: localhost
--  Run: psql -U postgres -d safety_bmw_test -f migration_materia_prima.sql
--
--  Inventario por piezas que NO está a la venta (raw_materials) y su historial
--  (raw_material_movements). Una parte se puede pasar al inventario vendible: sale de aquí
--  como TO_INVENTORY y entra al producto como una entrada (inventory_movements, tipo IN,
--  referencia MP-<id del movimiento>).
--
--  Con spring.jpa.hibernate.ddl-auto=update Hibernate crea las tablas solo, y el backend
--  actualiza role_sections_section_check y da la sección a los roles de gestión al arrancar
--  (RoleSectionConstraintMigration); este script es para ambientes sin ddl-auto.
--  Idempotente — puede correrse sobre una base que ya lo tenga.
-- ============================================================

CREATE TABLE IF NOT EXISTS raw_materials (
    id                  BIGSERIAL PRIMARY KEY,
    tienda_id           BIGINT NOT NULL REFERENCES tiendas(id),
    name                VARCHAR(200) NOT NULL,
    description         TEXT,
    barcode             VARCHAR(100),
    cost                NUMERIC(12,2) NOT NULL DEFAULT 0,
    stock               INTEGER NOT NULL DEFAULT 0,
    min_stock           INTEGER NOT NULL DEFAULT 0,
    category_id         BIGINT REFERENCES categories(id),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    linked_product_id   BIGINT REFERENCES products(id),
    created_by_user_id  BIGINT REFERENCES users(id),
    updated_by_user_id  BIGINT REFERENCES users(id),
    deleted_by_user_id  BIGINT REFERENCES users(id),
    deleted_at          TIMESTAMP,
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP,
    CONSTRAINT uk_raw_materials_tienda_barcode UNIQUE (tienda_id, barcode)
);

CREATE TABLE IF NOT EXISTS raw_material_movements (
    id                BIGSERIAL PRIMARY KEY,
    raw_material_id   BIGINT NOT NULL REFERENCES raw_materials(id),
    user_id           BIGINT NOT NULL REFERENCES users(id),
    type              VARCHAR(15) NOT NULL CHECK (type IN ('IN', 'OUT', 'TO_INVENTORY')),
    quantity          INTEGER NOT NULL,
    previous_stock    INTEGER NOT NULL,
    new_stock         INTEGER NOT NULL,
    reason            VARCHAR(255),
    product_id        BIGINT REFERENCES products(id),
    created_at        TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_raw_material_movements_material ON raw_material_movements(raw_material_id);

-- Sección nueva en los roles.
ALTER TABLE role_sections DROP CONSTRAINT IF EXISTS role_sections_section_check;
ALTER TABLE role_sections ADD CONSTRAINT role_sections_section_check
    CHECK (section IN ('DASHBOARD', 'POS', 'INVENTORY', 'SALES', 'CASH_CUTS', 'REPORTS', 'USERS', 'ROLES', 'APARTADOS', 'RAW_MATERIALS'));

-- Los roles de gestión la reciben de entrada (los demás, desde Roles y Permisos).
INSERT INTO role_sections (role_id, section)
SELECT r.id, 'RAW_MATERIALS' FROM roles r
WHERE r.name IN ('ADMIN', 'SUPERVISOR', 'SUPER_ADMIN')
  AND NOT EXISTS (SELECT 1 FROM role_sections rs WHERE rs.role_id = r.id AND rs.section = 'RAW_MATERIALS');
