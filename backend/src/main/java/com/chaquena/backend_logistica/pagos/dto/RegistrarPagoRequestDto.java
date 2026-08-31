package com.chaquena.backend_logistica.pagos.dto;

import com.chaquena.backend_logistica.pedidos.domain.TipoPagoEnum;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrarPagoRequestDto {

    @NotNull(message = "El tipo de pago es obligatorio")
    private TipoPagoEnum tipoPago;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero")
    private BigDecimal monto;

    /** Solo efectivo: lo que el cliente puso sobre la mesa. */
    @DecimalMin(value = "0.00", message = "El monto entregado no puede ser negativo")
    private BigDecimal montoEntregado;

    /** Codigo de operacion de Yape/Plin o voucher del POS fisico. */
    @Size(max = 120, message = "La referencia no puede exceder 120 caracteres")
    private String referencia;

    /**
     * Efectivo se confirma en el acto. Billetera y tarjeta quedan pendientes
     * hasta que el cajero verifique la acreditacion, salvo que se marque aqui.
     */
    private Boolean confirmado;

    private String observacion;
}
