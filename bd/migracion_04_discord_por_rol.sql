-- =====================================================================
-- Migracion 04 - La cuenta de Discord es unica por papel, no por persona
-- =====================================================================
--
-- PROBLEMA
-- --------
-- `personas.discord_user_id` tenia una restriccion UNIQUE en la tabla
-- padre, que comparten trabajadores y clientes. Asi, una misma cuenta de
-- Discord no podia ser a la vez la de un trabajador y la de un cliente, y
-- eso ocurre en cuanto alguien del personal le escribe al bot de clientes:
--
--   1. El bot OUT recibe un pedido de una cuenta desconocida y abre una
--      ficha anonima de cliente con ese snowflake.
--   2. La misma persona escribe `/vincular` en el bot IN con su correo de
--      trabajador.
--   3. El UPDATE de su fila en `personas` choca con la del cliente y la
--      vinculacion falla por clave duplicada.
--
-- La comprobacion de IdentidadBotService no lo evitaba porque solo busca
-- entre trabajadores, y eso es lo correcto: lo que estaba mal era la
-- restriccion.
--
-- SOLUCION
-- --------
-- La columna baja a cada tabla hija, con su propia restriccion:
--
--   * `trabajadores.discord_user_id` - UNIQUE: dos trabajadores no pueden
--     compartir cuenta, o el kardex atribuiria movimientos a quien no los
--     hizo.
--   * `clientes.discord_user_id`     - UNIQUE: una cuenta, una ficha de
--     cliente.
--
-- Entre las dos tablas el mismo snowflake si puede repetirse, que es
-- justo el caso que antes se rechazaba.
--
-- POR QUE A MANO Y NO POR HIBERNATE
-- ---------------------------------
-- `spring.jpa.hibernate.ddl-auto=update` crearia las dos columnas nuevas,
-- pero vacias, y nunca quitaria la vieja: la restriccion que provoca el
-- fallo seguiria viva en `personas`. Hay que copiar el dato y borrar la
-- columna a mano.
--
-- Las restricciones llevan el mismo nombre que declaran las entidades
-- (@UniqueConstraint en Trabajador y Cliente) para que Hibernate las
-- reconozca al arrancar y no cree otra igual con un nombre generado.
--
-- Si alguna persona con cuenta de Discord no fuera ni trabajador ni
-- cliente, el script aborta antes de tocar nada: borrar la columna
-- perderia ese dato sin avisar.
--
-- El script es idempotente: sobre un esquema ya migrado no copia nada y
-- deja las restricciones como estaban.
--
-- COMO APLICARLA
-- --------------
--   docker exec -i bd-logistica psql -U admin_logistica -d logistica_db \
--       -v ON_ERROR_STOP=1 -f - < bd/migracion_04_discord_por_rol.sql
--
-- Justo despues, reconstruir el backend: el jar anterior sigue leyendo
-- `personas.discord_user_id` y falla en cuanto la columna desaparece.
--   docker compose up -d --build backend-logistica
--
-- Y refrescar el volcado:  (cd bd && ./watch_schema.sh)
-- =====================================================================

BEGIN;

-- 1. Las columnas nuevas, en cada papel.
ALTER TABLE trabajadores ADD COLUMN IF NOT EXISTS discord_user_id VARCHAR(32);
ALTER TABLE clientes     ADD COLUMN IF NOT EXISTS discord_user_id VARCHAR(32);

DO $$
BEGIN
    -- Solo mientras exista la columna vieja: en un esquema ya migrado no
    -- queda nada que copiar.
    IF EXISTS (SELECT 1
               FROM information_schema.columns
               WHERE table_schema = current_schema()
                 AND table_name = 'personas'
                 AND column_name = 'discord_user_id') THEN

        -- 2. Nada puede quedarse por el camino.
        IF EXISTS (SELECT 1
                   FROM personas p
                   WHERE p.discord_user_id IS NOT NULL
                     AND NOT EXISTS (SELECT 1 FROM trabajadores t WHERE t.persona_id = p.id)
                     AND NOT EXISTS (SELECT 1 FROM clientes c WHERE c.persona_id = p.id)) THEN
            RAISE EXCEPTION 'Hay personas con cuenta de Discord que no son ni trabajador ni cliente: revisalas antes de migrar.';
        END IF;

        -- 3. Copiar el dato a la tabla hija que le corresponde.
        UPDATE trabajadores t
        SET discord_user_id = p.discord_user_id
        FROM personas p
        WHERE p.id = t.persona_id
          AND p.discord_user_id IS NOT NULL
          AND t.discord_user_id IS NULL;

        UPDATE clientes c
        SET discord_user_id = p.discord_user_id
        FROM personas p
        WHERE p.id = c.persona_id
          AND p.discord_user_id IS NOT NULL
          AND c.discord_user_id IS NULL;

        -- 4. Borrar la columna vieja; su restriccion UNIQUE se va con ella.
        ALTER TABLE personas DROP COLUMN discord_user_id;
    END IF;
END $$;

-- 5. La unicidad, ahora dentro de cada papel.
ALTER TABLE trabajadores DROP CONSTRAINT IF EXISTS uk_trabajadores_discord_user_id;
ALTER TABLE trabajadores ADD CONSTRAINT uk_trabajadores_discord_user_id UNIQUE (discord_user_id);

ALTER TABLE clientes DROP CONSTRAINT IF EXISTS uk_clientes_discord_user_id;
ALTER TABLE clientes ADD CONSTRAINT uk_clientes_discord_user_id UNIQUE (discord_user_id);

COMMIT;

-- Comprobacion: cuantas cuentas quedaron en cada papel.
SELECT 'trabajadores' AS tabla, count(discord_user_id) AS con_cuenta_discord FROM trabajadores
UNION ALL
SELECT 'clientes', count(discord_user_id) FROM clientes;
