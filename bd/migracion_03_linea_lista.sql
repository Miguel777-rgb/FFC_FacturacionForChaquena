-- =====================================================================
-- Migracion 03 - Recordar que platillo ya salio de cocina
-- =====================================================================
--
-- PROBLEMA
-- --------
-- `PATCH /api/v1/kds/detalles/{id}/listo` existe desde el principio y
-- devuelve un mensaje de confirmacion, pero no guardaba nada: lo unico que
-- tocaba de la fila era `modified_by`. En una pantalla de cocina que se
-- refresca sola cada quince segundos, eso significa que todos los tildes
-- por platillo desaparecen en el siguiente ciclo.
--
-- Es justo lo que no puede pasar en el caso que justifica el endpoint: una
-- comanda que sale por partes, donde el cocinero marca lo que ya despacho
-- para no repetirlo.
--
-- SOLUCION
-- --------
-- Dos columnas en `orden_detalles`:
--
--   * `listo`        - la bandera. NOT NULL con DEFAULT false, porque
--                      "todavia no salio" y "nadie lo escribio" no pueden
--                      quedar indistinguibles (misma regla que aplico la
--                      migracion 02 a las demas banderas del esquema).
--   * `tiempo_listo` - cuando salio. Se queda NULLABLE a proposito: el NULL
--                      significa "todavia no ha pasado", igual que
--                      `ordenes.tiempo_estimado_cocina_minutos`.
--
-- POR QUE A MANO Y NO POR HIBERNATE
-- ---------------------------------
-- `spring.jpa.hibernate.ddl-auto=update` agrega columnas, pero una columna
-- NOT NULL sin DEFAULT sobre una tabla que ya tiene filas la rechaza
-- PostgreSQL. Con el DEFAULT puesto aqui, Hibernate encuentra la columna ya
-- creada al arrancar y no intenta nada.
--
-- El DEFAULT ademas deja funcionando los INSERT hechos a mano que documenta
-- bd/README.md.
--
-- El script es idempotente: `IF NOT EXISTS` en las dos columnas y un
-- backfill que no encuentra nada la segunda vez.
--
-- COMO APLICARLA
-- --------------
--   docker exec -i bd-logistica psql -U admin_logistica -d logistica_db \
--       -v ON_ERROR_STOP=1 -f - < bd/migracion_03_linea_lista.sql
--
-- Despues, refrescar el volcado:  ./bd/watch_schema.sh
-- =====================================================================

BEGIN;

-- 1. La bandera, con su valor de partida ya puesto en la propia base.
ALTER TABLE orden_detalles
    ADD COLUMN IF NOT EXISTS listo BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. La marca de tiempo. Nullable: el nulo aqui significa algo.
ALTER TABLE orden_detalles
    ADD COLUMN IF NOT EXISTS tiempo_listo TIMESTAMP WITH TIME ZONE;

-- 3. Backfill de coherencia. Las lineas de comandas que ya cerraron cocina
--    salieron, por definicion: no tendria sentido que una comanda ENTREGADA
--    o CONCLUIDA mostrara sus platillos como pendientes en un historico.
--    Se les pone la hora de cierre de platillo de su propia comanda, que es
--    el unico momento real que consta; si esa comanda no la tiene sellada,
--    la linea queda en listo sin hora, que es honesto.
UPDATE orden_detalles d
SET listo = TRUE,
    tiempo_listo = o.tiempo_cierre_platillo
FROM ordenes o
WHERE o.id = d.orden_id
  AND d.listo = FALSE
  AND o.flag_cierre_platillo = TRUE;

COMMIT;

-- Comprobacion: cuantas lineas quedaron marcadas y cuantas pendientes.
SELECT listo, count(*) AS lineas
FROM orden_detalles
GROUP BY listo
ORDER BY listo;
