-- =====================================================================
-- Migracion 06 - El stock que ya habia entra en un lote inicial
-- =====================================================================
--
-- PROBLEMA
-- --------
-- Desde esta version cada insumo lleva su stock repartido en lotes
-- (`lotes_insumo`), con costo y vencimiento, y la suma de lo que queda en
-- los lotes de un insumo tiene que ser igual a `insumos.stock_actual`. El
-- stock que existia antes no esta en ningun lote: sin esta migracion, la
-- primera merma saldria de un lote nuevo aunque en el almacen hubiera kilos
-- mas viejos, y el valor del inventario no sabria que ese stock existe.
--
-- SOLUCION
-- --------
-- Un lote inicial por insumo con la diferencia entre su stock y lo que ya
-- tenga en lotes. Va sin proveedor, sin costo y sin vencimiento, porque no
-- se conocen: inventarlos haria pasar un dato que falta por uno real. Queda
-- con `control_origen_id` nulo, que es lo que lo marca como inicial.
--
-- Contar la diferencia y no el stock entero hace que se pueda volver a
-- ejecutar sin duplicar nada, y que no importe si entre el arranque y esta
-- migracion alguien ya registro una compra.
--
-- ORDEN
-- -----
-- Al reves que la migracion 05: PRIMERO se levanta el backend con esta
-- version, que crea `proveedores` y `lotes_insumo`, y DESPUES se aplica
-- esto. Mientras tanto el sistema funciona: lo que se consuma sin lotes
-- simplemente no descuenta de ninguno.
--
--   docker exec -i bd-logistica psql -U admin_logistica -d logistica_db \
--       -v ON_ERROR_STOP=1 -f - < bd/migracion_06_lotes_iniciales.sql
-- =====================================================================

BEGIN;

INSERT INTO lotes_insumo (id, insumo_id, cantidad_inicial, cantidad_restante,
                          created_by, date_created, modified_by, last_date_modified)
SELECT gen_random_uuid(),
       i.id,
       i.stock_actual - coalesce(l.en_lotes, 0),
       i.stock_actual - coalesce(l.en_lotes, 0),
       'MIGRACION_06', now(), 'MIGRACION_06', now()
FROM insumos i
LEFT JOIN (SELECT insumo_id, sum(cantidad_restante) AS en_lotes
           FROM lotes_insumo
           GROUP BY insumo_id) l ON l.insumo_id = i.id
WHERE i.stock_actual - coalesce(l.en_lotes, 0) > 0;

COMMIT;
