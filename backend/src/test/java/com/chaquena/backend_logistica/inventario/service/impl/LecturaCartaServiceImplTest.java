package com.chaquena.backend_logistica.inventario.service.impl;

import com.chaquena.backend_logistica.inventario.domain.CategoriaPlatillo;
import com.chaquena.backend_logistica.inventario.domain.ComplementoPlatillo;
import com.chaquena.backend_logistica.inventario.domain.Platillo;
import com.chaquena.backend_logistica.inventario.domain.TipoComplementoEnum;
import com.chaquena.backend_logistica.inventario.dto.ComplementoImportacionDto;
import com.chaquena.backend_logistica.inventario.dto.ImportacionCartaDto;
import com.chaquena.backend_logistica.inventario.dto.PlatilloImportacionDto;
import com.chaquena.backend_logistica.inventario.dto.ResultadoImportacionCartaDto;
import com.chaquena.backend_logistica.inventario.dto.SeccionImportacionDto;
import com.chaquena.backend_logistica.inventario.repository.CategoriaPlatilloRepository;
import com.chaquena.backend_logistica.inventario.repository.ComplementoPlatilloRepository;
import com.chaquena.backend_logistica.inventario.repository.PlatilloRepository;
import com.chaquena.backend_logistica.inventario.service.lectura.Tesseract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La importacion de lo revisado. Leer la foto se prueba aparte, con las reglas
 * ({@code ReglaLecturaCartaTest}) y con la carta de verdad
 * ({@code LecturaCartaRealTest}).
 */
@ExtendWith(MockitoExtension.class)
class LecturaCartaServiceImplTest {

    @Mock
    private Tesseract tesseract;

    @Mock
    private CategoriaPlatilloRepository categoriaRepository;

    @Mock
    private PlatilloRepository platilloRepository;

    @Mock
    private ComplementoPlatilloRepository complementoRepository;

    @InjectMocks
    private LecturaCartaServiceImpl servicio;

    private static PlatilloImportacionDto fila(String nombre, String precio, String descripcion, boolean vegetariano) {
        return PlatilloImportacionDto.builder()
                .nombre(nombre)
                .precio(new BigDecimal(precio))
                .descripcion(descripcion)
                .vegetariano(vegetariano)
                .build();
    }

    private static ImportacionCartaDto conSeccion(SeccionImportacionDto seccion) {
        return ImportacionCartaDto.builder().secciones(List.of(seccion)).build();
    }

    private void sinDatosPrevios() {
        when(categoriaRepository.findAll()).thenReturn(List.of());
        when(platilloRepository.findAll()).thenReturn(List.of());
        when(complementoRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void elPlatilloQueYaExisteCambiaDePrecioYElQueNoSeCrea() {
        CategoriaPlatillo parrillas = CategoriaPlatillo.builder().id(3).nombre("Parrillas").build();
        Platillo existente = Platillo.builder().id(UUID.randomUUID()).categoria(parrillas).nombre("Parrilla de Res")
                .descripcion("Corte de res con salchicha.").precioVentaBase(new BigDecimal("20.00")).build();
        when(categoriaRepository.findAll()).thenReturn(List.of(parrillas));
        when(platilloRepository.findAll()).thenReturn(List.of(existente));
        when(complementoRepository.findAll()).thenReturn(List.of());
        when(categoriaRepository.findById(3)).thenReturn(Optional.of(parrillas));
        when(platilloRepository.save(any(Platillo.class))).thenAnswer(i -> i.getArgument(0));

        ResultadoImportacionCartaDto resultado = servicio.importar(conSeccion(SeccionImportacionDto.builder()
                .categoriaId(3)
                .platillos(List.of(
                        // El acento y las mayusculas no cuentan: es el mismo platillo.
                        fila("PARRILLA DE RES", "21.00", "Corte de res con salchicha.", false),
                        fila("Parrilla de Cerdo", "20.00", "Corte de cerdo con salchicha.", false)))
                .build()));

        assertThat(resultado.getPlatillosActualizados()).isEqualTo(1);
        assertThat(resultado.getPlatillosCreados()).isEqualTo(1);
        assertThat(resultado.getSeccionesCreadas()).isZero();
        assertThat(existente.getPrecioVentaBase()).isEqualByComparingTo("21.00");
        // Sigue siendo el mismo platillo: no se le cambia el nombre que ya tenia.
        assertThat(existente.getNombre()).isEqualTo("Parrilla de Res");

        ArgumentCaptor<Platillo> guardados = ArgumentCaptor.forClass(Platillo.class);
        verify(platilloRepository, org.mockito.Mockito.times(2)).save(guardados.capture());
        Platillo nuevo = guardados.getAllValues().getLast();
        assertThat(nuevo.getNombre()).isEqualTo("Parrilla de Cerdo");
        assertThat(nuevo.getCategoria()).isSameAs(parrillas);
        assertThat(nuevo.getActivo()).isTrue();
    }

    @Test
    void unPlatilloIgualAlQueYaEstaNoSeGuardaOtraVez() {
        CategoriaPlatillo parrillas = CategoriaPlatillo.builder().id(3).nombre("Parrillas").build();
        Platillo existente = Platillo.builder().id(UUID.randomUUID()).categoria(parrillas).nombre("Parrilla de Res")
                .descripcion("Corte de res con salchicha.").precioVentaBase(new BigDecimal("20.00")).build();
        when(categoriaRepository.findAll()).thenReturn(List.of(parrillas));
        when(platilloRepository.findAll()).thenReturn(List.of(existente));
        when(complementoRepository.findAll()).thenReturn(List.of());
        when(categoriaRepository.findById(3)).thenReturn(Optional.of(parrillas));

        ResultadoImportacionCartaDto resultado = servicio.importar(conSeccion(SeccionImportacionDto.builder()
                .categoriaId(3)
                .platillos(List.of(fila("Parrilla de Res", "20.00", "Corte de res con salchicha.", false)))
                .build()));

        assertThat(resultado.getPlatillosSinCambios()).isEqualTo(1);
        verify(platilloRepository, never()).save(any());
    }

    @Test
    void laSeccionNuevaSeCreaUnaVezAunqueTraigaVariosPlatillos() {
        sinDatosPrevios();
        when(categoriaRepository.save(any(CategoriaPlatillo.class)))
                .thenAnswer(i -> {
                    CategoriaPlatillo guardada = i.getArgument(0);
                    guardada.setId(41);
                    return guardada;
                });
        when(platilloRepository.save(any(Platillo.class))).thenAnswer(i -> i.getArgument(0));

        ResultadoImportacionCartaDto resultado = servicio.importar(ImportacionCartaDto.builder()
                .secciones(List.of(
                        SeccionImportacionDto.builder().nombreNueva("Broaster")
                                .platillos(List.of(fila("Pollo Broaster (Mega 1)", "12.00", null, false))).build(),
                        SeccionImportacionDto.builder().nombreNueva("broaster")
                                .platillos(List.of(fila("Pollo Broaster (Mega 2)", "18.00", null, false))).build()))
                .build());

        assertThat(resultado.getSeccionesCreadas()).isEqualTo(1);
        assertThat(resultado.getPlatillosCreados()).isEqualTo(2);
        verify(categoriaRepository, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    void laMismaFilaEnDosFotosSeImportaUnaSolaVez() {
        sinDatosPrevios();
        when(categoriaRepository.findById(3))
                .thenReturn(Optional.of(CategoriaPlatillo.builder().id(3).nombre("Parrillas").build()));
        when(platilloRepository.save(any(Platillo.class))).thenAnswer(i -> i.getArgument(0));

        ResultadoImportacionCartaDto resultado = servicio.importar(ImportacionCartaDto.builder()
                .secciones(List.of(
                        SeccionImportacionDto.builder().categoriaId(3)
                                .platillos(List.of(fila("Parrilla de Res", "20.00", null, false))).build(),
                        SeccionImportacionDto.builder().categoriaId(3)
                                .platillos(List.of(fila("parrilla de res", "20.00", null, false))).build()))
                .build());

        assertThat(resultado.getPlatillosCreados()).isEqualTo(1);
    }

    @Test
    void laCasillaDeVegetarianoEncabezaLaDescripcionYNoSeRepite() {
        assertThat(LecturaCartaServiceImpl.descripcionCon("Verduras salteadas.", true))
                .isEqualTo("Vegetariano. Verduras salteadas.");
        assertThat(LecturaCartaServiceImpl.descripcionCon("Vegetariano. Verduras salteadas.", true))
                .isEqualTo("Vegetariano. Verduras salteadas.");
        assertThat(LecturaCartaServiceImpl.descripcionCon(null, true)).isEqualTo("Vegetariano.");
        assertThat(LecturaCartaServiceImpl.descripcionCon("  Verduras   salteadas. ", false))
                .isEqualTo("Verduras salteadas.");
        assertThat(LecturaCartaServiceImpl.descripcionCon(null, false)).isNull();
    }

    @Test
    void elAdicionalQueYaExisteSoloCambiaDePrecio() {
        ComplementoPlatillo chaufa = ComplementoPlatillo.builder().id(UUID.randomUUID()).nombre("Con Chaufa")
                .tipoComplemento(TipoComplementoEnum.OTROS).precioAdicional(new BigDecimal("4.00")).build();
        when(categoriaRepository.findAll()).thenReturn(List.of());
        when(platilloRepository.findAll()).thenReturn(List.of());
        when(complementoRepository.findAll()).thenReturn(List.of(chaufa));
        when(complementoRepository.save(any(ComplementoPlatillo.class))).thenAnswer(i -> i.getArgument(0));

        ResultadoImportacionCartaDto resultado = servicio.importar(ImportacionCartaDto.builder()
                .complementos(List.of(ComplementoImportacionDto.builder().nombre("con chaufa")
                        .tipo(TipoComplementoEnum.OTROS).precio(new BigDecimal("5.00")).build()))
                .build());

        assertThat(resultado.getComplementosActualizados()).isEqualTo(1);
        assertThat(resultado.getComplementosCreados()).isZero();
        assertThat(chaufa.getPrecioAdicional()).isEqualByComparingTo("5.00");
    }

    @Test
    void unaSeccionSinNombreNiIdNoImportaNada() {
        sinDatosPrevios();

        assertThatThrownBy(() -> servicio.importar(conSeccion(SeccionImportacionDto.builder()
                .platillos(List.of(fila("Parrilla de Res", "20.00", null, false)))
                .build())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sin seccion");

        verify(platilloRepository, never()).save(any());
    }
}
