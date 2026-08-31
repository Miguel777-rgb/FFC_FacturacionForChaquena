package com.chaquena.backend_logistica.pedidos.dto;

import com.chaquena.backend_logistica.pedidos.domain.CanalOrigenEnum;
import com.chaquena.backend_logistica.pedidos.domain.TipoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.domain.TipoPagoEnum;
import jakarta.validation.Valid;
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

    @NotNull(message = "El tipo de pago es obligatorio")
    private TipoPagoEnum tipoPago;

    private UUID promocionId;

    private String cuponCodigo;

    @NotEmpty(message = "La comanda debe tener al menos un platillo")
    @Valid
    private List<ItemOrdenRequestDto> items;
}
