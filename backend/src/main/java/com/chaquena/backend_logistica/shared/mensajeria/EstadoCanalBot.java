package com.chaquena.backend_logistica.shared.mensajeria;

/**
 * Salud de una de las dos identidades de mensajeria.
 *
 * <p>Separa dos cosas que desde fuera se confunden todo el tiempo. Un bot
 * <em>configurado</em> tiene credenciales en el {@code .env}; un bot
 * <em>conectado</em> ademas esta hablando con el proveedor ahora mismo. La
 * combinacion util es la tercera: configurado pero no conectado es un token
 * caducado o un intent que falta, y es el caso que hay que poder ver sin entrar
 * a los logs del contenedor.
 *
 * @param canal       IN para el personal, OUT para los clientes
 * @param configurado si tiene credenciales
 * @param conectado   si la conexion esta viva en este instante
 * @param identidad   con que cuenta aparece ante el proveedor, cuando se sabe
 * @param latenciaMs  ida y vuelta con la pasarela; nulo si el proveedor no
 *                    mantiene conexion persistente
 * @param detalle     una linea en castellano sobre lo que le falta o lo que hace
 */
public record EstadoCanalBot(
        CanalBot canal,
        boolean configurado,
        boolean conectado,
        String identidad,
        Long latenciaMs,
        String detalle) {

    public static EstadoCanalBot sinConfigurar(CanalBot canal, String detalle) {
        return new EstadoCanalBot(canal, false, false, null, null, detalle);
    }
}
