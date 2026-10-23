-- ============================================================
--  Boutique POS — Migración: códigos de producto generados por el sistema
--  DB: safety_bmw_test  |  host: localhost
--  Run: psql -U postgres -d safety_bmw_test -f migration_product_barcodes.sql
--
--  products.barcode_generated: true si el código lo asignó el sistema (ya no se edita).
--  tiendas.barcode_sequence: último consecutivo usado por la tienda (empieza en 0).
--  Formato del código: tienda (3 dígitos) + consecutivo (5 dígitos), ej. 00200001.
--  Con spring.jpa.hibernate.ddl-auto=update Hibernate lo crea solo; este script es para
--  ambientes sin ddl-auto. Idempotente.
-- ============================================================

ALTER TABLE products ADD COLUMN IF NOT EXISTS barcode_generated BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tiendas  ADD COLUMN IF NOT EXISTS barcode_sequence  INTEGER NOT NULL DEFAULT 0;
