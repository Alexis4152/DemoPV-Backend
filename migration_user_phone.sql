-- ============================================================
--  Boutique POS — Migración: teléfono de usuario
--  DB: safety_bmw_test  |  host: localhost
--  Run: psql -U postgres -d safety_bmw_test -f migration_user_phone.sql
--
--  Teléfono opcional por usuario (10 dígitos, solo números). SOLO sirve para iniciar
--  sesión en lugar del correo, con la misma contraseña; por eso es único. Con
--  spring.jpa.hibernate.ddl-auto=update Hibernate lo crea solo; este script es para
--  ambientes sin ddl-auto. Idempotente.
-- ============================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(15);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint c JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
        WHERE c.conrelid = 'users'::regclass AND c.contype = 'u' AND a.attname = 'phone'
    ) THEN
        ALTER TABLE users ADD CONSTRAINT uk_users_phone UNIQUE (phone);
    END IF;
END $$;
