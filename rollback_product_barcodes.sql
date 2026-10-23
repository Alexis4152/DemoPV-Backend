-- ============================================================
--  Boutique POS — ROLLBACK de migration_product_barcodes.sql
--  DB: safety_bmw_test  |  host: localhost
--  Run: psql -U postgres -d safety_bmw_test -f rollback_product_barcodes.sql
--
--  Quita la marca de "código generado" y el consecutivo por tienda. Los códigos ya
--  asignados (products.barcode) se CONSERVAN, pero vuelven a ser editables.
--  ⚠ Úsalo JUNTO con regresar el backend a la versión anterior: con la versión nueva y
--  ddl-auto=update, Hibernate vuelve a crear las columnas al arrancar (con el consecutivo
--  en 0, que se saltaría los códigos ya usados, pero ya no estarían bloqueados).
-- ============================================================

ALTER TABLE products DROP COLUMN IF EXISTS barcode_generated;
ALTER TABLE tiendas  DROP COLUMN IF EXISTS barcode_sequence;
