package com.chaquena.backend_logistica.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrarTrabajadorRequestDto {

    /**
     * Las expresiones regulares son las mismas que valida la pantalla en
     * {@code nucleo/validacion/patrones.ts}. Estan repetidas a proposito: la del
     * navegador es comodidad —avisa mientras se escribe— y esta es la que de
     * verdad protege, porque un POST con curl no pasa por el formulario. Si se
     * cambia una, hay que cambiar la otra.
     */
    @NotBlank(message = "{trabajador.dni.obligatorio}")
    @Pattern(regexp = "\\d{8}", message = "{trabajador.dni.formato}")
    private String dni;

    @NotBlank(message = "{trabajador.nombres.obligatorio}")
    @Pattern(regexp = "\\p{L}+([ '\u2019-]\\p{L}+)*", message = "{trabajador.nombres.formato}")
    private String nombres;

    @NotBlank(message = "{trabajador.apellidos.obligatorio}")
    @Pattern(regexp = "\\p{L}+([ '\u2019-]\\p{L}+)*", message = "{trabajador.apellidos.formato}")
    private String apellidos;

    @Email(message = "{trabajador.correo.formato}")
    private String correo;

    @NotBlank(message = "{trabajador.celular.obligatorio}")
    @Pattern(regexp = "(\\+?51 ?)?9\\d{8}", message = "{trabajador.celular.formato}")
    private String celular; // Ej: "51987654321" (con código de país)

    @NotNull(message = "{trabajador.cargo.obligatorio}")
    private Integer cargoId;

    @NotBlank(message = "{trabajador.username.obligatorio}")
    @Pattern(regexp = "[a-z][a-z0-9._-]{2,19}", message = "{trabajador.username.formato}")
    private String username;

    @NotBlank(message = "{trabajador.password.obligatorio}")
    @Pattern(regexp = "(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}",
            message = "{trabajador.password.formato}")
    private String password;
}