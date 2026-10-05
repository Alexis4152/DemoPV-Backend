-- ============================================================
--  Boutique POS — Migración: refresh tokens de sesión
--  DB: safety_bmw_test  |  host: localhost
--  Run: psql -U postgres -d safety_bmw_test -f migration_refresh_tokens.sql
--
--  Agrega la tabla que respalda el refresh token de sesión (ver RefreshToken.java /
--  AuthService en el backend): string opaco entregado como cookie httpOnly, vigente
--  app.jwt.refresh-expiration (8h por default) y revocable de verdad (a diferencia del
--  access token JWT, que solo se valida por firma). Idempotente — puede correrse sobre
--  una base que ya la tenga sin duplicar nada.
-- ============================================================

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    token       VARCHAR(64) NOT NULL UNIQUE,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    expires_at  TIMESTAMP NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Usado por revokeAllForUser (cerrar sesión en todos los dispositivos al cambiar
-- contraseña, ver AuthService#changePassword/#resetPassword).
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id);
