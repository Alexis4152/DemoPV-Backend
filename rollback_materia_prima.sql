-- ============================================================
--  Boutique POS — ROLLBACK de migration_materia_prima.sql
--  DB: safety_bmw_test  |  host: localhost
--  Run: psql -U postgres -d safety_bmw_test -f rollback_materia_prima.sql
--
--  Deshace la materia prima: borra sus tablas, quita la sección RAW_MATERIALS de los roles
--  (secciones y permisos finos) y deja la restricción de secciones como estaba.
--  Idempotente (IF EXISTS) y en una sola transacción.
--
--  ⚠ BORRA DATOS: el catálogo de materia prima y su historial. Las piezas que ya se pasaron
--  a inventario se QUEDAN en los productos (con su entrada "Desde materia prima" en el
--  historial del producto). Respalda la base antes:
--    pg_dump -U postgres -d safety_bmw_test -F c -f backup_antes_rollback.dump
--
--  ⚠ Úsalo JUNTO con regresar el backend a la versión anterior. Con la versión nueva y
--  spring.jpa.hibernate.ddl-auto=update, Hibernate vuelve a crear todo al arrancar.
-- ============================================================

BEGIN;

DROP TABLE IF EXISTS raw_material_movements;
DROP TABLE IF EXISTS raw_materials;

DELETE FROM role_sections WHERE section = 'RAW_MATERIALS';
DELETE FROM role_action_grants WHERE grant_key LIKE 'RAW_MATERIALS:%';

ALTER TABLE role_sections DROP CONSTRAINT IF EXISTS role_sections_section_check;
ALTER TABLE role_sections ADD CONSTRAINT role_sections_section_check
    CHECK (section IN ('DASHBOARD', 'POS', 'INVENTORY', 'SALES', 'CASH_CUTS', 'REPORTS', 'USERS', 'ROLES', 'APARTADOS'));

COMMIT;
