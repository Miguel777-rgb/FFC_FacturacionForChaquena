package com.chaquena.backend_logistica.reportes.dto;

import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenEnum;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * El local de un vistazo.
 *
 * <p>Reune en una sola respuesta lo que hasta ahora habia que ir a buscar a
 * cinco endpoints distintos —ventas, cocina, salon, caja y la bandeja de
 * eventos—, porque una pantalla que abre el dueno al llegar no puede costar
 * cinco viajes ni quedar a medio pintar si uno de ellos falla.
 *
 * <p>Hay dos clases de cifra aqui dentro y conviene no confundirlas. Las de
 * {@link Ventas} y {@link Operacion} miran el rango pedido: son historia. Las de
 * {@link AhoraMismo} no miran fechas en absoluto, son una foto del estado
 * presente del local, y por eso no cambian aunque se pida el reporte del mes
 * pasado.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableroDto {

    private ZonedDateTime desde;
    private ZonedDateTime hasta;
    private Ventas ventas;
    private Operacion operacion;
    private AhoraMismo ahoraMismo;
    private List<PorEstado> porEstado;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Ventas {
        private BigDecimal total;
        private long comandas;
        /** Total dividido entre comandas; cero cuando no hubo ninguna. */
        private BigDecimal ticketPromedio;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Operacion {
        private long comandasCanceladas;
        private long comandasFraudulentas;
        /** Promedio de minutos entre que cocina arranca el plato y lo canta. Nulo si nadie cerro ninguno. */
        private Double minutosPromedioCocina;
        private Integer minutosObjetivoCocina;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AhoraMismo {
        private long comandasAbiertas;
        private long mesasOcupadas;
        private long mesasActivas;
        private long insumosBajoMinimo;
        private long pagosPorAcreditar;
        private long alertasDeFraude;
        private long eventosOutboxPendientes;
        private long eventosOutboxEnError;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PorEstado {
        private EstadoOrdenEnum estado;
        private long comandas;
        private BigDecimal total;
    }
}
