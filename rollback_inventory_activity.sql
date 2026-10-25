-- ============================================================
--  Boutique POS — ROLLBACK de migration_inventory_activity.sql
--  DB: safety_bmw_test  |  host: localhost
--  Run: psql -U postgres -d safety_bmw_test -f rollback_inventory_activity.sql
--
--  ⚠ BORRA la bitácora de inventario y el origen de los movimientos (los movimientos en
--  sí se conservan). Úsalo JUNTO con regresar el backend a la versión anterior: con la
--  versión nueva y ddl-auto=update, Hibernate vuelve a crear todo al arrancar.
-- ============================================================

DROP TABLE IF EXISTS inventory_audit_logs;
DROP INDEX IF EXISTS idx_inv_movements_created;
ALTER TABLE inventory_movements DROP COLUMN IF EXISTS source;
