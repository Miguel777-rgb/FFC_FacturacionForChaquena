-- =====================================================================
-- Migracion 01 - Alinear el esquema con la herencia JOINED de Persona
-- =====================================================================
--
-- PROBLEMA
-- --------
-- Las tablas `clientes` y `transportistas` se crearon originalmente como
-- tablas independientes, con su propia clave primaria `id` y sus propias
-- columnas de datos personales (nombre_razon_social, telefono_whatsapp,
-- placa_vehiculo, nombres_conductor...).
--
-- Despues las entidades se refactorizaron para heredar de `Persona` con
-- InheritanceType.JOINED, de modo que la clave primaria pasa a ser
-- `persona_id` y los datos personales viven en la tabla `personas`.
--
-- Como `spring.jpa.hibernate.ddl-auto=update` solo AGREGA columnas y nunca
-- borra ni re-apunta nada, las dos tablas quedaron con los dos esquemas
-- superpuestos. Consecuencias medidas:
--
--   1. `clientes.id` es NOT NULL sin valor por defecto y sigue siendo la
--      clave primaria, mientras Hibernate solo inserta `persona_id`.
--      Resultado: INSERTAR UN CLIENTE FALLA SIEMPRE con
--      'null value in column "id" of relation "clientes"'.
--
--   2. Las claves foraneas de `ordenes`, `cupones`,
--      `calificaciones_feedback` y `whatsapp_sesiones` apuntan a
--      `clientes(id)`, pero Hibernate genera los JOIN contra
--      `clientes(persona_id)`. Las dos columnas no coinciden.
--
--   3. Lo mismo ocurre en `transportistas`, que ademas conserva
--      `placa_vehiculo` cuando las placas ya viven en la tabla `vehiculos`.
--
-- SOLUCION
-- --------
-- Reconstruir las dos tablas. El script aborta si tienen filas, para no
-- perder datos por accidente. Tras ejecutarlo, al arrancar el backend
-- Hibernate las vuelve a crear con la clave primaria correcta y rehace las
-- claves foraneas que faltan.
--
-- USO
-- ---
--   docker exec -i bd-logistica psql -U <usuario> -d <bd> \
--       -v ON_ERROR_STOP=1 -f - < bd/migracion_01_esquema_herencia.sql
--   ./mvnw spring-boot:run     # Hibernate recrea las tablas
--
-- Si las tablas YA tienen datos en produccion, no uses este script: hay que
-- migrar las filas a `personas` primero.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 0. Verificacion de seguridad: abortar si hay datos que se perderian
-- ---------------------------------------------------------------------
DO $$
DECLARE
    filas_clientes       bigint;
    filas_transportistas bigint;
BEGIN
    SELECT count(*) INTO filas_clientes       FROM clientes;
    SELECT count(*) INTO filas_transportistas FROM transportistas;

    IF filas_clientes > 0 OR filas_transportistas > 0 THEN
        RAISE EXCEPTION
            'Abortado: clientes tiene % fila(s) y transportistas % fila(s). '
            'Este script solo es seguro con las tablas vacias.',
            filas_clientes, filas_transportistas;
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- 1. Soltar toda clave foranea que apunte a clientes o transportistas
--    Los nombres los genera Hibernate y cambian entre entornos, asi que
--    se resuelven desde el catalogo en lugar de escribirlos a mano.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    fk record;
BEGIN
    FOR fk IN
        SELECT con.conname, cl.relname AS tabla
        FROM pg_constraint con
        JOIN pg_class cl  ON cl.oid  = con.conrelid
        JOIN pg_class ref ON ref.oid = con.confrelid
        WHERE con.contype = 'f'
          AND ref.relname IN ('clientes', 'transportistas')
    LOOP
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', fk.tabla, fk.conname);
        RAISE NOTICE 'FK soltada: %.%', fk.tabla, fk.conname;
    END LOOP;
END $$;

-- ---------------------------------------------------------------------
-- 2. Eliminar las tablas con el esquema superpuesto
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS clientes;
DROP TABLE IF EXISTS transportistas;

-- ---------------------------------------------------------------------
-- 3. Corregir el CHECK de ordenes.estado
--    Hibernate crea los CHECK solo al crear la tabla, nunca al
--    actualizarla, asi que el constraint se quedo sin los estados PAGADO
--    y CONCLUIDO que se agregaron al enum.
-- ---------------------------------------------------------------------
ALTER TABLE ordenes DROP CONSTRAINT IF EXISTS ordenes_estado_check;
ALTER TABLE ordenes ADD CONSTRAINT ordenes_estado_check CHECK (estado IN (
    'ENCOLADO', 'EN_PREPARACION', 'EN_DESPACHO', 'ENTREGADO',
    'PAGADO', 'CONCLUIDO', 'CANCELADO', 'FRAUDULENTO'));

COMMIT;

-- Tras el COMMIT, arranca el backend: Hibernate recrea `clientes` y
-- `transportistas` con persona_id como clave primaria y vuelve a crear las
-- claves foraneas de ordenes, cupones, calificaciones_feedback,
-- whatsapp_sesiones, vehiculos y orden_delivery_info.
