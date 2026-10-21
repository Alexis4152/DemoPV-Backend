-- ============================================================
--  Boutique POS — Migración: máximo de tiendas por supervisor (plan contratado)
--  DB: safety_bmw_test  |  host: localhost
--  Run: psql -U postgres -d safety_bmw_test -f migration_supervisor_plan.sql
--
--  users.max_tiendas: solo para SUPERVISOR, lo fija SUPER_ADMIN. NULL = sin límite (así
--  quedan los supervisores que ya existen). Cuentan solo las tiendas activas. Con
--  spring.jpa.hibernate.ddl-auto=update Hibernate lo crea solo; este script es para
--  ambientes sin ddl-auto. Idempotente.
-- ============================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS max_tiendas INTEGER;
