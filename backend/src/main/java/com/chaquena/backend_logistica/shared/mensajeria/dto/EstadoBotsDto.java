package com.chaquena.backend_logistica.shared.mensajeria.dto;

import com.chaquena.backend_logistica.shared.mensajeria.EstadoCanalBot;
import lombok.*;

import java.util.List;

/**
 * Diagnostico completo del canal de mensajeria.
 *
 * <p>Los bots son la unica parte del sistema que falla en silencio: un token
 * caducado no rompe ninguna pantalla, solo hace que el almacenero escriba
 * {@code /stock} y no le conteste nadie. Esta respuesta existe para que ese
 * fallo se vea desde la trastienda en vez de descubrirse a mitad de servicio.
 *
 * <p>Reune tres capas que fallan por separado: el proveedor configurado, la
 * conexion de cada bot, y las personas vinculadas —porque un bot perfectamente
 * conectado tampoco atiende a nadie si nadie corrio {@code /vincular}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstadoBotsDto {

    /** Lo que dice {@code app.mensajeria.proveedor}. */
    private String proveedorPedido;

    /** El adaptador que quedo en servicio. Si no coincide con el pedido, ahi esta el fallo. */
    private String proveedorActivo;

    /** Nombres de los adaptadores compilados, para saber que valores son validos. */
    private List<String> proveedoresDisponibles;

    /** Si el proveedor activo tiene credenciales para enviar algo. */
    private Boolean disponible;

    /** Filas que admite una lista desplegable en este proveedor: 25 en Discord, 10 en WhatsApp. */
    private Integer maxOpciones;

    /** Estado de cada identidad: IN atiende al personal, OUT a los clientes. */
    private List<EstadoCanalBot> canales;

    private long trabajadoresVinculados;
    private long trabajadoresActivos;

    /** Conversaciones de pedido a medio hacer y todavia sin caducar. */
    private long sesionesClienteAbiertas;

    private List<VinculacionBotDto> vinculaciones;
}
