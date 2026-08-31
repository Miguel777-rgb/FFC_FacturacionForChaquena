package com.chaquena.backend_logistica.clientes.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Cliente que no quiere identificarse. Todo es opcional: el sistema le asigna
 * un documento provisional para poder asociarle la comanda.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClienteAnonimoRequestDto {

    @Size(max = 100, message = "El nombre de referencia no puede exceder 100 caracteres")
    private String nombreReferencia;

    @Size(max = 20, message = "El celular no puede exceder 20 caracteres")
    private String celular;

    private String direccionHabitual;

    /**
     * Cuenta del proveedor de mensajeria con la que pidio. Es lo que permite
     * reconocerle la proxima vez sin pedirle datos: quien pide por bot no da su
     * documento, pero siempre vuelve con la misma cuenta.
     */
    @Size(max = 32, message = "El identificador de Discord no puede exceder 32 caracteres")
    private String discordUserId;
}
