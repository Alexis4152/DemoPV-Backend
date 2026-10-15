-- ============================================================
--  Boutique POS — Migración: cajas, movimientos de efectivo, reembolsos y entrega de turno
--  DB: safety_bmw_test  |  host: localhost
--  Run: psql -U postgres -d safety_bmw_test -f migration_cajas_movimientos.sql
--
--  Respalda CashRegister / CashMovement / Reimbursement y los campos nuevos de CashCut
--  (recepción de caja al abrir, arqueo al cerrar, entrega al siguiente turno). Con
--  spring.jpa.hibernate.ddl-auto=update Hibernate crea todo esto solo; este script es para
--  ambientes sin ddl-auto. Idempotente — puede correrse sobre una base que ya lo tenga.
--
--  Las tiendas no necesitan una caja dada de alta: la primera vez que alguien consulta
--  las cajas de una tienda sin ninguna, el backend crea "Caja 1". Los cortes anteriores
--  quedan con cash_register_id en NULL y siguen funcionando (sin entrega de turno).
-- ============================================================

-- Conteo ciego al cerrar corte (opcional por tienda, "Datos de la tienda").
ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS blind_cash_count BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS cash_registers (
    id                          BIGSERIAL PRIMARY KEY,
    tienda_id                   BIGINT NOT NULL REFERENCES tiendas(id),
    name                        VARCHAR(60) NOT NULL,
    is_active                   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMP,
    -- entrega pendiente mientras la caja está cerrada (NULL si hay corte abierto)
    pending_amount              NUMERIC(12,2),
    pending_count_method        VARCHAR(20),
    pending_counted_by_user_id  BIGINT REFERENCES users(id),
    pending_counted_at          TIMESTAMP,
    last_cut_id                 BIGINT REFERENCES cash_cuts(id)
);
CREATE INDEX IF NOT EXISTS idx_cash_registers_tienda ON cash_registers(tienda_id);

ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS cash_register_id             BIGINT REFERENCES cash_registers(id);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS previous_cut_id              BIGINT REFERENCES cash_cuts(id);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS expected_opening_amount      NUMERIC(12,2);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS received_count_method        VARCHAR(20);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS receive_note                 TEXT;
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS handover_status              VARCHAR(12) NOT NULL DEFAULT 'NONE';
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS handover_difference          NUMERIC(12,2);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS handover_resolution          VARCHAR(25);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS handover_resolved_by_user_id BIGINT REFERENCES users(id);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS handover_resolved_at         TIMESTAMP;
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS handover_resolution_note     TEXT;
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS entries_total                NUMERIC(12,2);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS withdrawals_total            NUMERIC(12,2);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS reimbursements_total         NUMERIC(12,2);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS counted_amount               NUMERIC(12,2);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS cash_difference              NUMERIC(12,2);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS count_method                 VARCHAR(20);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS counted_by_user_id           BIGINT REFERENCES users(id);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS count_evidence_path          VARCHAR(300);
ALTER TABLE cash_cuts ADD COLUMN IF NOT EXISTS handover_amount              NUMERIC(12,2);
CREATE INDEX IF NOT EXISTS idx_cash_cuts_register ON cash_cuts(cash_register_id);

CREATE TABLE IF NOT EXISTS reimbursements (
    id                          BIGSERIAL PRIMARY KEY,
    tienda_id                   BIGINT NOT NULL REFERENCES tiendas(id),
    person_user_id              BIGINT NOT NULL REFERENCES users(id),
    amount                      NUMERIC(12,2) NOT NULL,
    reason                      VARCHAR(150),
    source_cut_id               BIGINT REFERENCES cash_cuts(id),
    status                      VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    created_at                  TIMESTAMP,
    paid_at                     TIMESTAMP,
    paid_registered_by_user_id  BIGINT REFERENCES users(id),
    payment_source              VARCHAR(10),
    payment_cut_id              BIGINT REFERENCES cash_cuts(id),
    external_method             VARCHAR(15),
    external_paid_by_user_id    BIGINT REFERENCES users(id),
    reference                   VARCHAR(100),
    receipt_path                VARCHAR(300)
);
CREATE INDEX IF NOT EXISTS idx_reimbursements_tienda_status ON reimbursements(tienda_id, status);

CREATE TABLE IF NOT EXISTS cash_movements (
    id                      BIGSERIAL PRIMARY KEY,
    tienda_id               BIGINT NOT NULL REFERENCES tiendas(id),
    cash_register_id        BIGINT REFERENCES cash_registers(id),
    cash_cut_id             BIGINT REFERENCES cash_cuts(id),
    received_by_cut_id      BIGINT REFERENCES cash_cuts(id),
    type                    VARCHAR(20) NOT NULL,
    amount                  NUMERIC(12,2) NOT NULL,
    concept                 VARCHAR(150),
    person_user_id          BIGINT REFERENCES users(id),
    destination             VARCHAR(10),
    reference               VARCHAR(100),
    receipt_path            VARCHAR(300),
    registered_by_user_id   BIGINT NOT NULL REFERENCES users(id),
    created_at              TIMESTAMP,
    occurred_at             TIMESTAMP,
    late_registered         BOOLEAN NOT NULL DEFAULT FALSE,
    expected_remaining      NUMERIC(12,2),
    counted_remaining       NUMERIC(12,2),
    reimbursement_id        BIGINT REFERENCES reimbursements(id)
);
CREATE INDEX IF NOT EXISTS idx_cash_movements_cut      ON cash_movements(cash_cut_id);
CREATE INDEX IF NOT EXISTS idx_cash_movements_register ON cash_movements(cash_register_id);
CREATE INDEX IF NOT EXISTS idx_cash_movements_received ON cash_movements(received_by_cut_id);
