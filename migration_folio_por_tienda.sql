-- ============================================================
--  Boutique POS — Migración: folio de ventas/apartados por tienda
--  DB: safety_bmw_test  |  host: localhost
--  Run: psql -U postgres -d safety_bmw_test -f migration_folio_por_tienda.sql
--
--  Hasta ahora el "folio" que ve el cliente/cajero era simplemente el id (BIGSERIAL)
--  global de sales/apartados — compartido entre TODAS las tiendas, así que si la tienda A
--  vendía algo le tocaba el folio 1, y la siguiente venta (de la tienda B) salía folio 2 en
--  vez de folio 1 también. Esta migración agrega una columna `folio` nueva en sales y
--  apartados, consecutiva POR TIENDA (id sigue siendo la llave real, sin tocar: folio es
--  solo para mostrar). Con spring.jpa.hibernate.ddl-auto=update Hibernate crea las columnas
--  solo, pero el backfill/numeración de lo histórico y los contadores de tiendas SOLO los
--  hace este script — es indispensable correrlo aunque uses ddl-auto=update.
--  Idempotente (puede correrse varias veces sin duplicar nada).
-- ============================================================

-- 1) Contadores por tienda (de dónde sigue el próximo folio de cada tipo).
ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS last_sale_folio BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tiendas ADD COLUMN IF NOT EXISTS last_apartado_folio BIGINT NOT NULL DEFAULT 0;

-- 2) Columna folio en sales/apartados (nullable por ahora, se llena abajo antes del NOT NULL).
ALTER TABLE sales ADD COLUMN IF NOT EXISTS folio BIGINT;
ALTER TABLE apartados ADD COLUMN IF NOT EXISTS folio BIGINT;
ALTER TABLE apartados ADD COLUMN IF NOT EXISTS sale_folio BIGINT;

-- 3) Backfill: renumera todo lo histórico, consecutivo POR TIENDA, en el mismo orden en
--    que se fueron creando (por id). Las ventas sin tienda (filas muy viejas, antes de que
--    tienda_id existiera) quedan agrupadas aparte por COALESCE(tienda_id, -1) — nunca van a
--    chocar con una tienda real porque ningún id de tienda es negativo.
UPDATE sales s
SET folio = ranked.rn
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY COALESCE(tienda_id, -1) ORDER BY id) AS rn
    FROM sales
) ranked
WHERE s.id = ranked.id AND s.folio IS NULL;

UPDATE apartados a
SET folio = ranked.rn
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY tienda_id ORDER BY id) AS rn
    FROM apartados
) ranked
WHERE a.id = ranked.id AND a.folio IS NULL;

-- 4) sale_folio de cada apartado ya completado: el folio (no el id) de la venta que generó.
UPDATE apartados a
SET sale_folio = s.folio
FROM sales s
WHERE a.sale_id = s.id AND a.sale_folio IS NULL;

-- 5) NOT NULL ahora que todo quedó numerado.
ALTER TABLE sales ALTER COLUMN folio SET NOT NULL;
ALTER TABLE apartados ALTER COLUMN folio SET NOT NULL;

-- 6) Único por tienda (NULLs de tienda_id en sales, si los hay, no chocan entre sí — Postgres
--    no considera NULL = NULL en una restricción UNIQUE).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_sales_tienda_folio') THEN
        ALTER TABLE sales ADD CONSTRAINT uk_sales_tienda_folio UNIQUE (tienda_id, folio);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_apartados_tienda_folio') THEN
        ALTER TABLE apartados ADD CONSTRAINT uk_apartados_tienda_folio UNIQUE (tienda_id, folio);
    END IF;
END $$;

-- 7) Deja el contador de cada tienda en el último folio ya usado, para que la siguiente
--    venta/apartado continúe desde ahí en vez de reiniciar en 1.
UPDATE tiendas t
SET last_sale_folio = sub.max_folio
FROM (SELECT tienda_id, MAX(folio) AS max_folio FROM sales WHERE tienda_id IS NOT NULL GROUP BY tienda_id) sub
WHERE t.id = sub.tienda_id AND sub.max_folio > t.last_sale_folio;

UPDATE tiendas t
SET last_apartado_folio = sub.max_folio
FROM (SELECT tienda_id, MAX(folio) AS max_folio FROM apartados GROUP BY tienda_id) sub
WHERE t.id = sub.tienda_id AND sub.max_folio > t.last_apartado_folio;
