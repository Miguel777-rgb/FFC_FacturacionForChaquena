package com.chaquena.backend_logistica.inventario.service.impl;

import com.chaquena.backend_logistica.auth.service.TrabajadorContexto;
import com.chaquena.backend_logistica.inventario.domain.*;
import com.chaquena.backend_logistica.inventario.dto.MovimientoRequestDto;
import com.chaquena.backend_logistica.inventario.repository.*;
import com.chaquena.backend_logistica.inventario.service.ReglaLotes;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventarioServiceImplTest {

    @Mock private InsumoRepository insumoRepository;
    @Mock private ControlInsumoRepository controlInsumoRepository;
    @Mock private LoteInsumoRepository loteRepository;
    @Mock private ProveedorRepository proveedorRepository;
    @Mock private PlatilloRepository platilloRepository;
    @Mock private ComplementoPlatilloRepository complementoRepository;
    @Mock private TrabajadorContexto trabajadorContexto;
    @Mock private ResumenLotes resumenLotes;

    @InjectMocks
    private InventarioServiceImpl servicio;

    private final UUID insumoId = UUID.randomUUID();
    private Insumo pollo;

    @BeforeEach
    void preparar() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("almacen1", null, List.of()));
        pollo = Insumo.builder().id(insumoId).nombre("Pechuga de pollo").unidadMedida("KG")
                .stockActual(new BigDecimal("8")).stockMinimo(BigDecimal.ONE).build();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void conMovimientoPosible() {
        when(trabajadorContexto.idActualObligatorio()).thenReturn(UUID.randomUUID());
        when(insumoRepository.findByIdParaActualizar(insumoId)).thenReturn(Optional.of(pollo));
        when(insumoRepository.save(any(Insumo.class))).thenAnswer(i -> i.getArgument(0));
        when(controlInsumoRepository.save(any(ControlInsumo.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void unaMermaNoAceptaProveedorNiCostoNiVencimiento() {
        MovimientoRequestDto merma = MovimientoRequestDto.builder()
                .insumoId(insumoId).tipoControl(TipoControlInsumoEnum.MERMA_DESPERDICIO)
                .cantidad(BigDecimal.ONE).motivoObservacion("Se quemo").costoUnitario(new BigDecimal("11"))
                .build();

        assertThatThrownBy(() -> servicio.registrarMovimiento(merma))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entrada por compra");
        verifyNoInteractions(insumoRepository, loteRepository);
    }

    @Test
    void laCompraCreaSuLoteConProveedorCostoYVencimiento() {
        conMovimientoPosible();
        Proveedor avicola = Proveedor.builder().id(UUID.randomUUID()).nombre("Avicola del Sur").activo(true).build();
        when(proveedorRepository.findById(avicola.getId())).thenReturn(Optional.of(avicola));
        LocalDate vence = ReglaLotes.hoy().plusDays(3);

        servicio.registrarMovimiento(MovimientoRequestDto.builder()
                .insumoId(insumoId).tipoControl(TipoControlInsumoEnum.ENTRADA_COMPRA)
                .cantidad(new BigDecimal("10")).motivoObservacion("Compra del lunes")
                .proveedorId(avicola.getId()).costoUnitario(new BigDecimal("11.50")).fechaVencimiento(vence)
                .build());

        ArgumentCaptor<LoteInsumo> lote = ArgumentCaptor.forClass(LoteInsumo.class);
        verify(loteRepository).save(lote.capture());
        assertThat(lote.getValue().getCantidadInicial()).isEqualByComparingTo("10");
        assertThat(lote.getValue().getCantidadRestante()).isEqualByComparingTo("10");
        assertThat(lote.getValue().getProveedor()).isSameAs(avicola);
        assertThat(lote.getValue().getCostoUnitario()).isEqualByComparingTo("11.50");
        assertThat(lote.getValue().getFechaVencimiento()).isEqualTo(vence);
        assertThat(pollo.getStockActual()).isEqualByComparingTo("18");
    }

    @Test
    void noSeCompraAUnProveedorDadoDeBaja() {
        // El proveedor se valida antes de tocar al trabajador o al insumo.
        Proveedor deBaja = Proveedor.builder().id(UUID.randomUUID()).nombre("Viejo").activo(false).build();
        when(proveedorRepository.findById(deBaja.getId())).thenReturn(Optional.of(deBaja));

        assertThatThrownBy(() -> servicio.registrarMovimiento(MovimientoRequestDto.builder()
                .insumoId(insumoId).tipoControl(TipoControlInsumoEnum.ENTRADA_COMPRA)
                .cantidad(BigDecimal.ONE).motivoObservacion("Compra").proveedorId(deBaja.getId())
                .build()))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("dado de baja");
        verify(loteRepository, never()).save(any());
    }

    @Test
    void laMermaSaleDeLoQueVenceAntes() {
        conMovimientoPosible();
        LoteInsumo tarde = LoteInsumo.builder().cantidadInicial(new BigDecimal("5"))
                .cantidadRestante(new BigDecimal("5")).fechaVencimiento(ReglaLotes.hoy().plusDays(10)).build();
        LoteInsumo pronto = LoteInsumo.builder().cantidadInicial(new BigDecimal("3"))
                .cantidadRestante(new BigDecimal("3")).fechaVencimiento(ReglaLotes.hoy().plusDays(1)).build();
        when(loteRepository.findByInsumoIdAndCantidadRestanteGreaterThan(insumoId, BigDecimal.ZERO))
                .thenReturn(new ArrayList<>(List.of(tarde, pronto)));

        servicio.registrarMovimiento(MovimientoRequestDto.builder()
                .insumoId(insumoId).tipoControl(TipoControlInsumoEnum.MERMA_DESPERDICIO)
                .cantidad(new BigDecimal("4")).motivoObservacion("Se malogro").build());

        assertThat(pronto.getCantidadRestante()).isEqualByComparingTo("0");
        assertThat(tarde.getCantidadRestante()).isEqualByComparingTo("4");
        verify(loteRepository).saveAll(anyList());
        assertThat(pollo.getStockActual()).isEqualByComparingTo("4");
    }
}
