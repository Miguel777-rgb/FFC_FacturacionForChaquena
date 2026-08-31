-- =====================================================================
-- Migracion 02 - Prohibir el nulo donde el nulo no significa nada
-- =====================================================================
--
-- PROBLEMA
-- --------
-- Dos familias de columnas admitian NULL sin que ese NULL quisiera decir
-- nada:
--
--   1. AUDITORIA. `created_by` y `date_created` siempre fueron NOT NULL,
--      pero `modified_by` y `last_date_modified` no. Peor: el callback
--      @PrePersist ponia la fecha de modificacion y NO el autor, asi que
--      toda fila recien creada nacia con last_date_modified lleno y
--      modified_by vacio. La auditoria obligatoria del diseno no auditaba
--      quien fue el ultimo en tocar la fila.
--
--   2. CANTIDADES Y BANDERAS. Contadores (puntos de fidelidad, score de
--      fraude, reintentos del outbox), importes de descuento, vuelto y
--      banderas de estado (activo, activa, flags de cierre) admitian NULL,
--      de modo que "cero" y "nadie lo escribio" quedaban indistinguibles.
--      Varias de ellas YA tenian valor por defecto en @PrePersist: la
--      columna seguia siendo nullable solo porque
--      `spring.jpa.hibernate.ddl-auto=update` agrega columnas pero jamas
--      cambia las que ya existen.
--
-- SOLUCION
-- --------
-- Rellenar lo que este en NULL con el valor que le corresponde, poner las
-- columnas en NOT NULL y dejarles un DEFAULT en la propia base para que un
-- INSERT hecho a mano (como los que documenta bd/README.md) siga
-- funcionando. El script es idempotente: si una columna ya esta en NOT NULL
-- vuelve a ejecutarse sin efecto.
--
-- QUE NO TOCA, Y POR QUE
-- ----------------------
--   * `mesas.capacidad` - una mesa sin capacidad declarada es un dato que
--     falta, y cero personas seria falso (el DTO exige minimo 1).
--   * `ordenes.tiempo_estimado_cocina_minutos` y
--     `orden_delivery_info.tiempo_estimado_minutos` - el NULL significa
--     "todavia no hay promesa"; cero minutos seria una promesa imposible y
--     DeliveryServiceImpl ya distingue los dos casos.
--   * Fechas de hecho (hora_despacho, fecha_canje, tiempo_cierre_*),
--     textos opcionales y claves foraneas opcionales: ahi el NULL si
--     significa algo.
--
-- USO
-- ---
--   docker exec -i bd-logistica psql -U admin_logistica -d logistica_db \
--       -v ON_ERROR_STOP=1 -f - < bd/migracion_02_no_nulos.sql
--   cd bd && ./watch_schema.sh    # refrescar el volcado versionado
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 0. Verificacion de seguridad: puntajes sin valor
-- ---------------------------------------------------------------------
-- Un puntaje va de 1 a 5. Si alguna calificacion vieja quedo sin puntaje no
-- hay relleno honesto posible (cero esta fuera de la escala y tres seria
-- inventar una opinion), asi que el script se detiene y lo decide una
-- persona.
DO $$
DECLARE
    sin_puntaje bigint;
BEGIN
    SELECT count(*) INTO sin_puntaje
    FROM calificaciones_feedback
    WHERE puntaje_atencion IS NULL
       OR puntaje_comida IS NULL
       OR puntaje_lugar IS NULL;

    IF sin_puntaje > 0 THEN
        RAISE EXCEPTION
            'Hay % calificaciones sin puntaje. La escala va de 1 a 5 y no hay '
            'valor por defecto honesto: revisalas o borralas antes de migrar.',
            sin_puntaje;
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- 1. Auditoria: modified_by y last_date_modified en todas las tablas
-- ---------------------------------------------------------------------
-- Se recorre el catalogo en vez de listar las 27 tablas a mano: la pareja
-- (created_by, date_created) existe en todas las que llevan auditoria, y
-- una fila nunca modificada se describe con created_by = modified_by, no
-- con un hueco.
DO $$
DECLARE
    t text;
BEGIN
    FOR t IN
        SELECT c.table_name
        FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.column_name = 'modified_by'
          AND c.is_nullable = 'YES'
    LOOP
        EXECUTE format(
            'UPDATE %I SET modified_by = COALESCE(created_by, ''SYSTEM'') '
            'WHERE modified_by IS NULL', t);
        EXECUTE format(
            'ALTER TABLE %I '
            '    ALTER COLUMN modified_by SET DEFAULT ''SYSTEM'', '
            '    ALTER COLUMN modified_by SET NOT NULL', t);
        RAISE NOTICE 'modified_by asegurado en %', t;
    END LOOP;

    FOR t IN
        SELECT c.table_name
        FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.column_name = 'last_date_modified'
          AND c.is_nullable = 'YES'
    LOOP
        EXECUTE format(
            'UPDATE %I SET last_date_modified = COALESCE(date_created, now()) '
            'WHERE last_date_modified IS NULL', t);
        EXECUTE format(
            'ALTER TABLE %I '
            '    ALTER COLUMN last_date_modified SET DEFAULT now(), '
            '    ALTER COLUMN last_date_modified SET NOT NULL', t);
        RAISE NOTICE 'last_date_modified asegurado en %', t;
    END LOOP;
END $$;

-- ---------------------------------------------------------------------
-- 2. Personas: clientes, trabajadores y transportistas
-- ---------------------------------------------------------------------
UPDATE clientes SET puntos_fidelidad     = 0     WHERE puntos_fidelidad IS NULL;
UPDATE clientes SET score_fraude         = 0     WHERE score_fraude IS NULL;
UPDATE clientes SET bloqueado_por_fraude = false WHERE bloqueado_por_fraude IS NULL;

ALTER TABLE clientes
    ALTER COLUMN puntos_fidelidad     SET DEFAULT 0,
    ALTER COLUMN puntos_fidelidad     SET NOT NULL,
    ALTER COLUMN score_fraude         SET DEFAULT 0,
    ALTER COLUMN score_fraude         SET NOT NULL,
    ALTER COLUMN bloqueado_por_fraude SET DEFAULT false,
    ALTER COLUMN bloqueado_por_fraude SET NOT NULL;

-- Una ficha de personal sin bandera es una ficha de alta: se da por activa.
UPDATE trabajadores   SET activo = true WHERE activo IS NULL;
UPDATE transportistas SET activo = true WHERE activo IS NULL;

ALTER TABLE trabajadores
    ALTER COLUMN activo SET DEFAULT true,
    ALTER COLUMN activo SET NOT NULL;
ALTER TABLE transportistas
    ALTER COLUMN activo SET DEFAULT true,
    ALTER COLUMN activo SET NOT NULL;

-- ---------------------------------------------------------------------
-- 3. Inventario y carta
-- ---------------------------------------------------------------------
UPDATE platillos             SET activo = true WHERE activo IS NULL;
UPDATE complementos_platillo SET activo = true WHERE activo IS NULL;
UPDATE vehiculos             SET activo = true WHERE activo IS NULL;

ALTER TABLE platillos
    ALTER COLUMN activo SET DEFAULT true,
    ALTER COLUMN activo SET NOT NULL;
ALTER TABLE complementos_platillo
    ALTER COLUMN activo SET DEFAULT true,
    ALTER COLUMN activo SET NOT NULL;
ALTER TABLE vehiculos
    ALTER COLUMN activo SET DEFAULT true,
    ALTER COLUMN activo SET NOT NULL;

-- En una promocion el descuento que no se usa vale cero, no "se ignora":
-- una promocion es por porcentaje o por importe, y la otra via queda en 0.
UPDATE promociones SET porcentaje_descuento  = 0     WHERE porcentaje_descuento IS NULL;
UPDATE promociones SET monto_descuento       = 0     WHERE monto_descuento IS NULL;
UPDATE promociones SET requiere_insumo_extra = false WHERE requiere_insumo_extra IS NULL;
UPDATE promociones SET activa                = true  WHERE activa IS NULL;

ALTER TABLE promociones
    ALTER COLUMN porcentaje_descuento  SET DEFAULT 0,
    ALTER COLUMN porcentaje_descuento  SET NOT NULL,
    ALTER COLUMN monto_descuento       SET DEFAULT 0,
    ALTER COLUMN monto_descuento       SET NOT NULL,
    ALTER COLUMN requiere_insumo_extra SET DEFAULT false,
    ALTER COLUMN requiere_insumo_extra SET NOT NULL,
    ALTER COLUMN activa                SET DEFAULT true,
    ALTER COLUMN activa                SET NOT NULL;

-- ---------------------------------------------------------------------
-- 4. Salon
-- ---------------------------------------------------------------------
UPDATE mesas SET activa = true WHERE activa IS NULL;

ALTER TABLE mesas
    ALTER COLUMN activa SET DEFAULT true,
    ALTER COLUMN activa SET NOT NULL;

-- ---------------------------------------------------------------------
-- 5. Comandas, delivery y cobros
-- ---------------------------------------------------------------------
UPDATE ordenes SET monto_descuento       = 0     WHERE monto_descuento IS NULL;
UPDATE ordenes SET scoring_riesgo_orden  = 0     WHERE scoring_riesgo_orden IS NULL;
UPDATE ordenes SET flag_cierre_recepcion = false WHERE flag_cierre_recepcion IS NULL;
UPDATE ordenes SET flag_cierre_platillo  = false WHERE flag_cierre_platillo IS NULL;
UPDATE ordenes SET flag_cierre_despacho  = false WHERE flag_cierre_despacho IS NULL;

ALTER TABLE ordenes
    ALTER COLUMN monto_descuento       SET DEFAULT 0,
    ALTER COLUMN monto_descuento       SET NOT NULL,
    ALTER COLUMN scoring_riesgo_orden  SET DEFAULT 0,
    ALTER COLUMN scoring_riesgo_orden  SET NOT NULL,
    ALTER COLUMN flag_cierre_recepcion SET DEFAULT false,
    ALTER COLUMN flag_cierre_recepcion SET NOT NULL,
    ALTER COLUMN flag_cierre_platillo  SET DEFAULT false,
    ALTER COLUMN flag_cierre_platillo  SET NOT NULL,
    ALTER COLUMN flag_cierre_despacho  SET DEFAULT false,
    ALTER COLUMN flag_cierre_despacho  SET NOT NULL;

UPDATE orden_delivery_info SET otp_verificado = false WHERE otp_verificado IS NULL;

ALTER TABLE orden_delivery_info
    ALTER COLUMN otp_verificado SET DEFAULT false,
    ALTER COLUMN otp_verificado SET NOT NULL;

-- En tarjeta y billetera no hay efectivo sobre el mostrador: lo entregado es
-- el importe exacto del cobro y el vuelto es cero.
UPDATE pagos SET monto_entregado = monto WHERE monto_entregado IS NULL;
UPDATE pagos SET vuelto          = 0     WHERE vuelto IS NULL;
UPDATE pagos SET es_fraudulento  = false WHERE es_fraudulento IS NULL;

ALTER TABLE pagos
    ALTER COLUMN monto_entregado SET NOT NULL,
    ALTER COLUMN vuelto          SET DEFAULT 0,
    ALTER COLUMN vuelto          SET NOT NULL,
    ALTER COLUMN es_fraudulento  SET DEFAULT false,
    ALTER COLUMN es_fraudulento  SET NOT NULL;

-- ---------------------------------------------------------------------
-- 6. Fidelizacion
-- ---------------------------------------------------------------------
UPDATE cupones SET porcentaje_descuento = 0 WHERE porcentaje_descuento IS NULL;
UPDATE cupones SET monto_descuento      = 0 WHERE monto_descuento IS NULL;

ALTER TABLE cupones
    ALTER COLUMN porcentaje_descuento SET DEFAULT 0,
    ALTER COLUMN porcentaje_descuento SET NOT NULL,
    ALTER COLUMN monto_descuento      SET DEFAULT 0,
    ALTER COLUMN monto_descuento      SET NOT NULL;

-- Los mismos valores que ConfiguracionLocal.porDefecto().
UPDATE configuracion_local SET porcentaje_descuento_cupon = 10.00 WHERE porcentaje_descuento_cupon IS NULL;
UPDATE configuracion_local SET dias_vigencia_cupon        = 30    WHERE dias_vigencia_cupon IS NULL;
UPDATE configuracion_local SET minutos_objetivo_cocina    = 20    WHERE minutos_objetivo_cocina IS NULL;

ALTER TABLE configuracion_local
    ALTER COLUMN porcentaje_descuento_cupon SET DEFAULT 10.00,
    ALTER COLUMN porcentaje_descuento_cupon SET NOT NULL,
    ALTER COLUMN dias_vigencia_cupon        SET DEFAULT 30,
    ALTER COLUMN dias_vigencia_cupon        SET NOT NULL,
    ALTER COLUMN minutos_objetivo_cocina    SET DEFAULT 20,
    ALTER COLUMN minutos_objetivo_cocina    SET NOT NULL;

-- ---------------------------------------------------------------------
-- 7. Calificaciones y outbox
-- ---------------------------------------------------------------------
ALTER TABLE calificaciones_feedback
    ALTER COLUMN puntaje_atencion SET NOT NULL,
    ALTER COLUMN puntaje_comida   SET NOT NULL,
    ALTER COLUMN puntaje_lugar    SET NOT NULL;

-- La entidad ya declaraba retry_count NOT NULL; la columna se creo antes y
-- ddl-auto=update no la corrigio nunca.
UPDATE outbox_events SET retry_count = 0 WHERE retry_count IS NULL;

ALTER TABLE outbox_events
    ALTER COLUMN retry_count SET DEFAULT 0,
    ALTER COLUMN retry_count SET NOT NULL;

COMMIT;
