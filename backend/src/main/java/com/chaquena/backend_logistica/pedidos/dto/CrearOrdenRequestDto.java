package com.chaquena.backend_logistica.pedidos.dto;

import com.chaquena.backend_logistica.pedidos.domain.CanalOrigenEnum;
import com.chaquena.backend_logistica.pedidos.domain.TipoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.domain.TipoPagoEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrearOrdenRequestDto {

    /** Opcional: una comanda de salon puede no tener cliente identificado. */
    private UUID clienteId;

    @NotNull(message = "El tipo de orden es obligatorio")
    private TipoOrdenEnum tipoOrden;

    private CanalOrigenEnum canalOrigen;

    /** Obligatorio cuando el tipo de orden es MESA. */
    private UUID mesaId;

    /** Obligatorio cuando el tipo de orden es DELIVERY. */
    private String direccionDelivery;

    /** El punto marcado en el mapa. Opcional; van las dos o ninguna. */
    @DecimalMin(value = "-90.0", message = "La latitud va de -90 a 90")
    @DecimalMax(value = "90.0", message = "La latitud va de -90 a 90")
    private Double latitudDelivery;

    @DecimalMin(value = "-180.0", message = "La longitud va de -180 a 180")
    @DecimalMax(value = "180.0", message = "La longitud va de -180 a 180")
    private Double longitudDelivery;

    @NotNull(message = "El tipo de pago es obligatorio")
    private TipoPagoEnum tipoPago;

    private UUID promocionId;

    private String cuponCodigo;

    @NotEmpty(message = "La comanda debe tener al menos un platillo")
    @Valid
    private List<ItemOrdenRequestDto> items;
}
