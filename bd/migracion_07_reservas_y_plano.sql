-- =============================================================================
-- Migracion 07: reservas en su propia tabla y mesas ubicadas en el plano.
--
-- SE APLICA DESPUES de arrancar el backend nuevo, como la 06: la tabla
-- reservas y las columnas del plano (columna, fila, ancho, alto, forma) las
-- crea Hibernate al arrancar, las del plano con su valor por defecto.
--
-- 1. Lo que habia en mesas.reservada_a_nombre_de / reservada_para pasa a ser
--    una reserva pendiente de 90 minutos, para tantas personas como sillas
--    tiene la mesa (el dato viejo no decia cuantas venian).
-- 2. Una mesa ya no guarda que esta reservada: lo calcula la agenda. Las que
--    estaban en RESERVADA quedan LIBRE.
-- 3. Se borran las dos columnas viejas.
-- 4. Las mesas de cada zona, que llegan todas apiladas en la celda 0,0, se
--    reparten en filas de cuatro con una celda de pasillo, en orden de numero.
--
-- Se puede ejecutar mas de una vez: cada paso comprueba si ya se hizo.
-- =============================================================================

BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'mesas' AND column_name = 'reservada_para') THEN

        INSERT INTO reservas (id, mesa_id, nombre, personas, inicio, duracion_minutos, estado,
                              created_by, date_created, modified_by, last_date_modified)
        SELECT gen_random_uuid(), m.id,
               coalesce(nullif(trim(m.reservada_a_nombre_de), ''), 'Sin nombre'),
               coalesce(m.capacidad, 1), m.reservada_para, 90, 'PENDIENTE',
               'MIGRACION_07', now(), 'MIGRACION_07', now()
        FROM mesas m
        WHERE m.reservada_para IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM reservas r
                          WHERE r.mesa_id = m.id AND r.inicio = m.reservada_para);

        ALTER TABLE mesas DROP COLUMN reservada_a_nombre_de;
        ALTER TABLE mesas DROP COLUMN reservada_para;
    END IF;
END $$;

UPDATE mesas SET estado = 'LIBRE', modified_by = 'MIGRACION_07', last_date_modified = now()
WHERE estado = 'RESERVADA';

WITH apiladas AS (
    SELECT coalesce(zona, '') AS zona
    FROM mesas
    GROUP BY coalesce(zona, '')
    HAVING count(*) > 1 AND count(DISTINCT (columna, fila)) = 1
), orden AS (
    SELECT id,
           row_number() OVER (PARTITION BY coalesce(zona, '') ORDER BY length(numero), numero) - 1 AS n
    FROM mesas
    WHERE coalesce(zona, '') IN (SELECT zona FROM apiladas)
)
UPDATE mesas m
SET columna = (o.n % 4) * 3,
    fila = (o.n / 4) * 3,
    ancho = 2,
    alto = 2,
    modified_by = 'MIGRACION_07',
    last_date_modified = now()
FROM orden o
WHERE o.id = m.id;

COMMIT;
