package com.chaquena.backend_logistica.inventario.service.impl;

import com.chaquena.backend_logistica.archivos.service.ArchivoService;
import com.chaquena.backend_logistica.inventario.domain.Insumo;
import com.chaquena.backend_logistica.inventario.domain.InsumoPlatillo;
import com.chaquena.backend_logistica.inventario.domain.Platillo;
import com.chaquena.backend_logistica.inventario.dto.PlatilloResponseDto;
import com.chaquena.backend_logistica.inventario.dto.RecetaItemDto;
import com.chaquena.backend_logistica.inventario.dto.RecetaRequestDto;
import com.chaquena.backend_logistica.inventario.repository.AlergenoRepository;
import com.chaquena.backend_logistica.inventario.repository.CategoriaPlatilloRepository;
import com.chaquena.backend_logistica.inventario.repository.InsumoRepository;
import com.chaquena.backend_logistica.inventario.repository.PlatilloRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Los pasos de la preparacion viajan con la receta, pero no se pisan por
 * descuido: quien guarda solo los insumos no los borra.
 */
@ExtendWith(MockitoExtension.class)
class PlatilloServiceImplTest {

    @Mock private PlatilloRepository platilloRepository;
    @Mock private CategoriaPlatilloRepository categoriaRepository;
    @Mock private InsumoRepository insumoRepository;
    @Mock private AlergenoRepository alergenoRepository;
    @Mock private ArchivoService archivoService;
    @Mock private CostoPlatillos costoPlatillos;

    @InjectMocks
    private PlatilloServiceImpl servicio;

    private final UUID platilloId = UUID.randomUUID();
    private final UUID insumoId = UUID.randomUUID();
    private Platillo caldo;
    private Insumo gallina;

    @BeforeEach
    void preparar() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("almacen1", null, List.of()));
        gallina = Insumo.builder().id(insumoId).nombre("Gallina").unidadMedida("KG").build();
        caldo = Platillo.builder().id(platilloId).nombre("Caldo de Gallina")
                .pasos(new ArrayList<>(List.of("Hervir la gallina con kion y cebolla china.", "Servir.")))
                .build();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    /** La receta que se guarda lleva una presa de gallina; cada prueba decide los pasos. */
    private RecetaRequestDto.RecetaRequestDtoBuilder conUnaPresa() {
        when(platilloRepository.findWithRecetaById(platilloId)).thenReturn(Optional.of(caldo));
        when(insumoRepository.findById(insumoId)).thenReturn(Optional.of(gallina));
        when(platilloRepository.save(any(Platillo.class))).thenAnswer(i -> i.getArgument(0));
        return RecetaRequestDto.builder().insumos(List.of(RecetaItemDto.builder()
                .insumoId(insumoId).cantidadRequerida(new BigDecimal("0.300")).build()));
    }

    @Test
    void guardarSoloLosInsumosConservaLosPasos() {
        servicio.reemplazarReceta(platilloId, conUnaPresa().build());

        assertThat(caldo.getPasos()).containsExactly("Hervir la gallina con kion y cebolla china.", "Servir.");
        assertThat(caldo.getReceta()).extracting(InsumoPlatillo::getInsumo).containsExactly(gallina);
    }

    @Test
    void losPasosQueLleganReemplazanALosAnterioresEnSuOrdenYSinEspaciosDeMas() {
        servicio.reemplazarReceta(platilloId, conUnaPresa()
                .pasos(List.of("  Sancochar la papa   amarilla ", "Freír el huevo.")).build());

        assertThat(caldo.getPasos()).containsExactly("Sancochar la papa amarilla", "Freír el huevo.");
        assertThat(caldo.getModifiedBy()).isEqualTo("almacen1");
    }

    @Test
    void unaListaVaciaQuitaTodosLosPasos() {
        servicio.reemplazarReceta(platilloId, conUnaPresa().pasos(List.of()).build());

        assertThat(caldo.getPasos()).isEmpty();
    }

    @Test
    void laFichaTraeLosPasosYElListadoNo() {
        assertThat(PlatilloResponseDto.conReceta(caldo).getPasos())
                .containsExactly("Hervir la gallina con kion y cebolla china.", "Servir.");
        assertThat(PlatilloResponseDto.fromEntity(caldo).getPasos()).isNull();
    }
}
