-- ============================================================
--  Boutique POS — Migración: Movimientos de inventario (origen + bitácora)
--  DB: safety_bmw_test  |  host: localhost
--  Run: psql -U postgres -d safety_bmw_test -f migration_inventory_activity.sql
--
--  inventory_movements.source: origen del movimiento (SALE, MERMA, STOCK_IMPORT...). Los
--  registros anteriores los clasifica MovementSourceBackfill al arrancar el backend.
--  inventory_audit_logs: bitácora de acciones de Inventario que no mueven stock (alta,
--  edición, baja, carga masiva, descargas, códigos, etiquetas).
--  Con spring.jpa.hibernate.ddl-auto=update Hibernate lo crea solo; este script es para
--  ambientes sin ddl-auto. Idempotente.
-- ============================================================

ALTER TABLE inventory_movements ADD COLUMN IF NOT EXISTS source VARCHAR(30);

CREATE TABLE IF NOT EXISTS inventory_audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    tienda_id   BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL REFERENCES users(id),
    action      VARCHAR(30)  NOT NULL,
    product_id  BIGINT       REFERENCES products(id),
    detail      TEXT,
    created_at  TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_inv_audit_tienda_created ON inventory_audit_logs (tienda_id, created_at);
CREATE INDEX IF NOT EXISTS idx_inv_movements_created ON inventory_movements (created_at);
