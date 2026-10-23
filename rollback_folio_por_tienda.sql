-- ============================================================
--  Boutique POS — ROLLBACK de migration_folio_por_tienda.sql
--  DB: safety_bmw_test  |  host: localhost
--  Run: psql -U postgres -d safety_bmw_test -f rollback_folio_por_tienda.sql
--
--  Quita el folio por tienda de ventas/apartados y los contadores de tiendas.
--  ⚠ Úsalo JUNTO con regresar el backend a la versión anterior: con la versión nueva y
--  ddl-auto=update, Hibernate vuelve a crear las columnas (sin numerar nada) al arrancar.
--  Los tickets/correos/reportes ya emitidos con el folio nuevo no se ven afectados (son
--  texto ya generado), pero el backend vuelto a la versión anterior otra vez va a mostrar
--  el id global como "folio".
-- ============================================================

ALTER TABLE sales DROP CONSTRAINT IF EXISTS uk_sales_tienda_folio;
ALTER TABLE apartados DROP CONSTRAINT IF EXISTS uk_apartados_tienda_folio;

ALTER TABLE sales DROP COLUMN IF EXISTS folio;
ALTER TABLE apartados DROP COLUMN IF EXISTS folio;
ALTER TABLE apartados DROP COLUMN IF EXISTS sale_folio;

ALTER TABLE tiendas DROP COLUMN IF EXISTS last_sale_folio;
ALTER TABLE tiendas DROP COLUMN IF EXISTS last_apartado_folio;
