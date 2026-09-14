package com.chaquena.backend_logistica.inventario.dto;

import com.chaquena.backend_logistica.inventario.domain.TipoControlInsumoEnum;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Movimiento manual de inventario. La cantidad siempre se envia en positivo:
 * el signo lo decide el tipo de control, para que el frontend no tenga que
 * conocer la regla contable.
 *
 * <p>Proveedor, costo y vencimiento son del lote que crea una entrada por
 * compra, y los tres son opcionales: una compra al paso en el mercado puede no
 * tener ninguno. En cualquier otro tipo de movimiento se rechazan.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimientoRequestDto {

    @NotNull(message = "El insumo es obligatorio")
    private UUID insumoId;

    @NotNull(message = "El tipo de control es obligatorio")
    private TipoControlInsumoEnum tipoControl;

    @NotNull(message = "La cantidad es obligatoria")
    @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor que cero")
    private BigDecimal cantidad;

    @NotBlank(message = "El motivo es obligatorio: es lo que hace auditable el movimiento")
    private String motivoObservacion;

    private UUID proveedorId;

    /** Soles por unidad de medida del insumo. */
    @DecimalMin(value = "0.0", message = "El costo no puede ser negativo")
    private BigDecimal costoUnitario;

    private LocalDate fechaVencimiento;
}
