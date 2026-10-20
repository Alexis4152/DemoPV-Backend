-- ============================================================
--  Boutique POS — ROLLBACK de migration_supervisor_plan.sql
--  DB: safety_bmw_test  |  host: localhost
--  Run: psql -U postgres -d safety_bmw_test -f rollback_supervisor_plan.sql
--
--  Quita el máximo de tiendas de los supervisores (vuelven a no tener límite).
--  ⚠ Úsalo JUNTO con regresar el backend a la versión anterior: con la versión nueva y
--  ddl-auto=update, Hibernate vuelve a crear la columna al arrancar.
-- ============================================================

ALTER TABLE users DROP COLUMN IF EXISTS max_tiendas;
