-- ============================================================
--  Boutique POS — Migración: bloqueo de cuenta por intentos fallidos de login
--  DB: safety_bmw_test  |  host: localhost
--  Run: psql -U postgres -d safety_bmw_test -f migration_login_lockout.sql
--
--  Agrega las columnas que respaldan el bloqueo temporal de cuenta tras varios intentos
--  fallidos de login (ver User.java / AuthService#login en el backend — hallazgo "Alto"
--  de la auditoría de código, capa complementaria al límite por IP de
--  RateLimitInterceptor). Idempotente — puede correrse sobre una base que ya las tenga
--  sin duplicar nada.
-- ============================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP;
