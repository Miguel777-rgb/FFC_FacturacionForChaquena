package com.chaquena.backend_logistica.inventario.dto;

import com.chaquena.backend_logistica.inventario.domain.Platillo;
import com.chaquena.backend_logistica.inventario.service.ReglaCosto;
import lombok.*;

import java.math.BigDecimal;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatilloResponseDto {

    private static final Comparator<Object> ALFABETICO = Collator.getInstance(Locale.of("es"));

    private UUID id;
    private Integer categoriaId;
    private String categoriaNombre;
    private String nombre;
    private String descripcion;
    private BigDecimal precioVentaBase;
    private Boolean activo;
    private List<RecetaItemDto> receta;

    /** La foto, servida en /api/v1/archivos/{fotoId}. Nulo si no tiene. */
    private UUID fotoId;
    private Integer tiempoPreparacionMinutos;
    private List<AlergenoDto> alergenos;

    /**
     * La receta por el ultimo costo con que entro cada insumo. Nulo si no hay
     * receta o si algun insumo nunca entro con costo: un costo parcial seria un
     * plato mas barato de lo que es.
     */
    private BigDecimal costo;
    /** Precio menos costo, en soles. */
    private BigDecimal margen;
    /** El margen sobre el precio, con un decimal. */
    private BigDecimal margenPorcentaje;
    /** Por que no hay costo: los insumos de la receta que nunca entraron con uno. */
    private List<String> insumosSinCosto;

    public static PlatilloResponseDto fromEntity(Platillo platillo) {
        return construir(platillo, false, null);
    }

    public static PlatilloResponseDto fromEntity(Platillo platillo, ReglaCosto.Costo costo) {
        return construir(platillo, false, costo);
    }

    public static PlatilloResponseDto conReceta(Platillo platillo) {
        return construir(platillo, true, null);
    }

    public static PlatilloResponseDto conReceta(Platillo platillo, ReglaCosto.Costo costo) {
        return construir(platillo, true, costo);
    }

    private static PlatilloResponseDto construir(Platillo platillo, boolean incluirReceta, ReglaCosto.Costo costo) {
        return PlatilloResponseDto.builder()
                .id(platillo.getId())
                .categoriaId(platillo.getCategoria() != null ? platillo.getCategoria().getId() : null)
                .categoriaNombre(platillo.getCategoria() != null ? platillo.getCategoria().getNombre() : null)
                .nombre(platillo.getNombre())
                .descripcion(platillo.getDescripcion())
                .precioVentaBase(platillo.getPrecioVentaBase())
                .activo(platillo.getActivo())
                .receta(incluirReceta && platillo.getReceta() != null
                        ? platillo.getReceta().stream().map(RecetaItemDto::fromEntity).toList()
                        : null)
                .fotoId(platillo.getFoto() != null ? platillo.getFoto().getId() : null)
                .tiempoPreparacionMinutos(platillo.getTiempoPreparacionMinutos())
                .alergenos(platillo.getAlergenos() == null ? List.of()
                        : platillo.getAlergenos().stream()
                                .map(AlergenoDto::fromEntity)
                                .sorted(Comparator.comparing(AlergenoDto::getNombre, ALFABETICO))
                                .toList())
                .costo(costo != null ? costo.costo() : null)
                .margen(costo != null ? costo.margen() : null)
                .margenPorcentaje(costo != null ? costo.margenPorcentaje() : null)
                .insumosSinCosto(costo != null ? costo.insumosSinCosto() : List.of())
                .build();
    }
}
