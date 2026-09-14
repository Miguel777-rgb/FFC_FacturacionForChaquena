package com.chaquena.backend_logistica.local.dto;

import com.chaquena.backend_logistica.local.domain.DatosLocal;
import com.chaquena.backend_logistica.local.domain.HorarioLocal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Los datos del local y su horario en un solo cuerpo: se leen y se guardan
 * juntos desde la misma pantalla.
 *
 * <p>El {@code PUT} reemplaza los textos, asi que un campo que llega vacio se
 * borra. Dos excepciones: sin {@code porcentajeIgv} se conserva el que habia,
 * y sin {@code horarios} el horario no se toca.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatosLocalDto {

    @Size(max = 120, message = "El nombre comercial admite hasta 120 caracteres")
    private String nombreComercial;

    @Pattern(regexp = "\\d{11}", message = "El RUC tiene 11 digitos")
    private String ruc;

    private String direccion;

    @Size(max = 20, message = "El telefono admite hasta 20 caracteres")
    private String telefono;

    @Email(message = "El correo no tiene un formato valido")
    @Size(max = 120, message = "El correo admite hasta 120 caracteres")
    private String correo;

    @DecimalMin(value = "0.00", message = "El IGV no puede ser negativo")
    @DecimalMax(value = "100.00", message = "El IGV no puede superar 100")
    private BigDecimal porcentajeIgv;

    @Valid
    private List<HorarioLocalDto> horarios;

    public static DatosLocalDto de(DatosLocal d, List<HorarioLocal> horarios) {
        return DatosLocalDto.builder()
                .nombreComercial(d.getNombreComercial())
                .ruc(d.getRuc())
                .direccion(d.getDireccion())
                .telefono(d.getTelefono())
                .correo(d.getCorreo())
                .porcentajeIgv(d.getPorcentajeIgv())
                .horarios(horarios.stream().map(HorarioLocalDto::fromEntity).toList())
                .build();
    }
}
