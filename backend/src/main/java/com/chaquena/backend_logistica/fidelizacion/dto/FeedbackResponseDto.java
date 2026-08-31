package com.chaquena.backend_logistica.fidelizacion.dto;

import com.chaquena.backend_logistica.pedidos.domain.CalificacionFeedback;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackResponseDto {

    private Long id;
    private UUID ordenId;
    private UUID clienteId;
    private Integer puntajeAtencion;
    private Integer puntajeComida;
    private Integer puntajeLugar;
    private Double promedio;
    private String comentario;
    private ZonedDateTime fecha;

    /** Cupon emitido si esta calificacion alcanzo el umbral N. */
    private CuponResponseDto cuponGenerado;
    private String mensajeFidelizacion;

    public static FeedbackResponseDto fromEntity(CalificacionFeedback f) {
        double promedio = (f.getPuntajeAtencion() + f.getPuntajeComida() + f.getPuntajeLugar()) / 3.0;
        return FeedbackResponseDto.builder()
                .id(f.getId())
                .ordenId(f.getOrden() != null ? f.getOrden().getId() : null)
                .clienteId(f.getCliente() != null ? f.getCliente().getId() : null)
                .puntajeAtencion(f.getPuntajeAtencion())
                .puntajeComida(f.getPuntajeComida())
                .puntajeLugar(f.getPuntajeLugar())
                .promedio(Math.round(promedio * 100) / 100.0)
                .comentario(f.getComentario())
                .fecha(f.getDateCreated())
                .build();
    }
}
