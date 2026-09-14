-- =====================================================================
-- Migracion 05 - Cada comanda congela su IGV y guarda el nivel aplicado
-- =====================================================================
--
-- PROBLEMA
-- --------
-- Los precios de la carta ya incluyen el IGV, y el ticket tiene que
-- desglosarlo. La tasa vive en `datos_local`, pero si una comanda la
-- leyera de ahi al mostrarse, cambiar el porcentaje manana reescribiria el
-- desglose de las ventas de hoy. Por eso la comanda guarda la tasa con la
-- que se vendio, en `ordenes.porcentaje_igv`, `NOT NULL`.
--
-- Hibernate no puede crear esa columna solo: su `update` hace un
-- `ALTER TABLE ... ADD COLUMN ... NOT NULL` que Postgres rechaza en cuanto
-- la tabla tiene filas, lo registra como error y sigue arrancando sin la
-- columna. La primera comanda nueva fallaria despues.
--
-- SOLUCION
-- --------
--   * `ordenes.porcentaje_igv` - se agrega con 18.00 para las comandas que
--     ya existen (la tasa general vigente cuando se vendieron) y queda
--     `NOT NULL DEFAULT 18.00`, para que los INSERT manuales de bd/README.md
--     sigan funcionando.
--   * `ordenes.nivel_lealtad_nombre` - nulo con sentido: ninguna comanda
--     anterior aplico un nivel, porque los niveles no existian.
--
-- Las tablas nuevas (`datos_local`, `horarios_local`, `niveles_lealtad`)
-- las crea Hibernate al arrancar: no tienen filas previas.
--
-- ORDEN
-- -----
-- Aplicar ANTES de levantar el backend con la version que trae estos
-- campos. Es idempotente: se puede volver a ejecutar sin efecto.
--
--   docker exec -i bd-logistica psql -U admin_logistica -d logistica_db \
--       -v ON_ERROR_STOP=1 -f - < bd/migracion_05_igv_y_nivel_en_ordenes.sql
-- =====================================================================

BEGIN;

ALTER TABLE ordenes ADD COLUMN IF NOT EXISTS porcentaje_igv numeric(5, 2);
UPDATE ordenes SET porcentaje_igv = 18.00 WHERE porcentaje_igv IS NULL;
ALTER TABLE ordenes ALTER COLUMN porcentaje_igv SET DEFAULT 18.00;
ALTER TABLE ordenes ALTER COLUMN porcentaje_igv SET NOT NULL;

ALTER TABLE ordenes ADD COLUMN IF NOT EXISTS nivel_lealtad_nombre varchar(60);

COMMIT;
