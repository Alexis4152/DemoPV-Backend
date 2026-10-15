-- ============================================================
--  Boutique POS — ROLLBACK de migration_cajas_movimientos.sql
--  DB: safety_bmw_test  |  host: localhost
--  Run: psql -U postgres -d safety_bmw_test -f rollback_cajas_movimientos.sql
--
--  Revierte cajas, movimientos de efectivo, reembolsos y entrega de turno: borra las
--  tablas cash_movements, reimbursements y cash_registers, y las columnas nuevas de
--  cash_cuts y tiendas. Los cortes existentes se conservan (solo pierden esas columnas).
--
--  ⚠ DESTRUCTIVO: se pierden todos los movimientos, reembolsos, cajas y los datos de
--  arqueo / entrega de turno. Respalda antes:
--    pg_dump -U postgres -d safety_bmw_test -F c -f backup_antes_rollback.dump
--
--  OJO: si el backend sigue corriendo con spring.jpa.hibernate.ddl-auto=update y el
--  código nuevo, Hibernate volverá a crear todo al reiniciar. Despliega primero el
--  código anterior.
--
--  Idempotente y transaccional — si algo falla no se aplica nada.
-- ============================================================

BEGIN;

-- Orden inverso a la migración: primero lo que depende de las demás tablas.
-- (Al borrar tablas/columnas, PostgreSQL elimina también sus índices y FKs propias.)

DROP TABLE IF EXISTS cash_movements;
DROP TABLE IF EXISTS reimbursements;

-- cash_cuts.cash_register_id apunta a cash_registers, así que va antes de borrar la tabla.
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS cash_register_id;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS previous_cut_id;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS expected_opening_amount;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS received_count_method;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS receive_note;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS handover_status;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS handover_difference;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS handover_resolution;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS handover_resolved_by_user_id;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS handover_resolved_at;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS handover_resolution_note;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS entries_total;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS withdrawals_total;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS reimbursements_total;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS counted_amount;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS cash_difference;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS count_method;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS counted_by_user_id;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS count_evidence_path;
ALTER TABLE cash_cuts DROP COLUMN IF EXISTS handover_amount;
DROP INDEX IF EXISTS idx_cash_cuts_register;  -- ya cae con la columna; por si quedó suelto

DROP TABLE IF EXISTS cash_registers;

ALTER TABLE tiendas DROP COLUMN IF EXISTS blind_cash_count;

COMMIT;
