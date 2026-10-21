-- ============================================================
--  Boutique POS — ROLLBACK de migration_user_phone.sql
--  DB: safety_bmw_test  |  host: localhost
--  Run: psql -U postgres -d safety_bmw_test -f rollback_user_phone.sql
--
--  Quita el teléfono de los usuarios (y su restricción de único, sea cual sea su nombre).
--  ⚠ BORRA los teléfonos capturados; después de esto solo se puede entrar con correo.
--  ⚠ Úsalo JUNTO con regresar el backend a la versión anterior: con la versión nueva y
--  ddl-auto=update, Hibernate vuelve a crear la columna al arrancar.
-- ============================================================

ALTER TABLE users DROP COLUMN IF EXISTS phone;
