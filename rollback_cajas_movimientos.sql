-- ============================================================
--  Boutique POS — ROLLBACK de migration_cajas_movimientos.sql
--  DB: safety_bmw_test  |  host: localhost
--  Run: psql -U postgres -d safety_bmw_test -f rollback_cajas_movimientos.sql
--
--  Deshace TODO lo que agrega migration_cajas_movimientos.sql: cajas, movimientos de
--  efectivo, reembolsos, faltantes, destinatarios del reporte de fin de día, y las columnas
--  nuevas de cash_cuts y tiendas. Idempotente (IF EXISTS) y en una sola transacción: si algo
--  falla, no se aplica nada.
--
--  ⚠ BORRA DATOS sin posibilidad de recuperarlos: cajas, entradas/salidas/retiros,
--  reembolsos, faltantes y sus pagos, arqueos (contado/diferencia), entregas entre turnos y
--  sus resoluciones. Respalda la base antes:
--    pg_dump -U postgres -d safety_bmw_test -F c -f backup_antes_rollback.dump
--
--  ⚠ Úsalo JUNTO con regresar el backend a la versión anterior. Con la versión nueva y
--  spring.jpa.hibernate.ddl-auto=update, Hibernate vuelve a crear todo al arrancar.
--
--  Lo que NO toca (existía antes de la migración): cash_cuts y sus columnas originales
--  (opening_amount, closing_amount, expenses, totales de ventas, etc.), las ventas y su
--  relación con el corte. Las fotos de comprobantes subidas quedan en uploads/cash/ y se
--  pueden borrar a mano.
-- ============================================================

BEGIN;

-- 1. Tablas que dependen de otras (de las hojas hacia la raíz).
DROP TABLE IF EXISTS shortage_payments;
DROP TABLE IF EXISTS shortages;
DROP TABLE IF EXISTS cash_report_recipients;
DROP TABLE IF EXISTS cash_movements;      -- se lleva su restricción cash_movements_type_check
DROP TABLE IF EXISTS reimbursements;

-- 2. Columnas nuevas de cash_cuts (sus llaves foráneas, índices y restricciones CHECK se
--    eliminan junto con cada columna). Antes de borrar cash_registers, porque
--    cash_cuts.cash_register_id apunta a ella.
ALTER TABLE cash_cuts
    DROP COLUMN IF EXISTS cash_register_id,
    DROP COLUMN IF EXISTS previous_cut_id,
    DROP COLUMN IF EXISTS expected_opening_amount,
    DROP COLUMN IF EXISTS received_count_method,
    DROP COLUMN IF EXISTS receive_note,
    DROP COLUMN IF EXISTS handover_status,
    DROP COLUMN IF EXISTS handover_difference,
    DROP COLUMN IF EXISTS handover_resolution,
    DROP COLUMN IF EXISTS handover_resolved_by_user_id,
    DROP COLUMN IF EXISTS handover_resolved_at,
    DROP COLUMN IF EXISTS handover_resolution_note,
    DROP COLUMN IF EXISTS entries_total,
    DROP COLUMN IF EXISTS withdrawals_total,
    DROP COLUMN IF EXISTS reimbursements_total,
    DROP COLUMN IF EXISTS loans_out_total,
    DROP COLUMN IF EXISTS counted_amount,
    DROP COLUMN IF EXISTS cash_difference,
    DROP COLUMN IF EXISTS count_method,
    DROP COLUMN IF EXISTS counted_by_user_id,
    DROP COLUMN IF EXISTS count_evidence_path,
    DROP COLUMN IF EXISTS handover_amount,
    DROP COLUMN IF EXISTS arqueo_status,
    DROP COLUMN IF EXISTS arqueo_resolution,
    DROP COLUMN IF EXISTS arqueo_resolved_by_user_id,
    DROP COLUMN IF EXISTS arqueo_resolved_at,
    DROP COLUMN IF EXISTS arqueo_resolution_note;

-- 3. Cajas (cash_registers.last_cut_id apunta a cash_cuts; se va con la tabla).
DROP TABLE IF EXISTS cash_registers;

-- 4. Columnas nuevas de tiendas.
ALTER TABLE tiendas
    DROP COLUMN IF EXISTS blind_cash_count,
    DROP COLUMN IF EXISTS report_to_supervisor;

COMMIT;
