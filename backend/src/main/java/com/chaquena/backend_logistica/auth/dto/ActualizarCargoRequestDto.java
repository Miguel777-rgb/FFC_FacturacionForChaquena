package com.chaquena.backend_logistica.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActualizarCargoRequestDto {

    @NotBlank(message = "El nombre del cargo es obligatorio")
    private String nombre;

    private String descripcion;

    /**
     * Conjunto completo de roles del cargo, no un anadido: lo que llegue aqui
     * reemplaza a lo que hubiera. Se pide siempre —aunque sea vacio— porque la
     * alternativa, tratar el nulo como "dejalo como estaba", hace que quitarle
     * el ultimo rol a un cargo sea indistinguible de no tocarlo.
     */
    @NotNull(message = "Envia la lista de roles, aunque este vacia")
    private List<Integer> rolIds;
}
