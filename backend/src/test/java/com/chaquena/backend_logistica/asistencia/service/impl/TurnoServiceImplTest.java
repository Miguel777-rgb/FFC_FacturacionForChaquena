package com.chaquena.backend_logistica.asistencia.service.impl;

import com.chaquena.backend_logistica.asistencia.domain.Turno;
import com.chaquena.backend_logistica.asistencia.dto.TurnoDto;
import com.chaquena.backend_logistica.asistencia.repository.TurnoRepository;
import com.chaquena.backend_logistica.auth.domain.Trabajador;
import com.chaquena.backend_logistica.auth.repository.TrabajadorRepository;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TurnoServiceImplTest {

    @Mock private TurnoRepository turnoRepository;
    @Mock private TrabajadorRepository trabajadorRepository;

    @InjectMocks
    private TurnoServiceImpl servicio;

    private final UUID mozoId = UUID.randomUUID();
    private final LocalDate martes = LocalDate.of(2026, 9, 15);
    private Trabajador mozo;

    @BeforeEach
    void preparar() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null, List.of()));
        mozo = Trabajador.builder().id(mozoId).nombres("Luis").apellidos("Quispe").activo(true).build();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private TurnoDto turno(LocalDate fecha, int inicio, int fin) {
        return TurnoDto.builder().trabajadorId(mozoId).fecha(fecha).inicio(LocalTime.of(inicio, 0))
                .fin(LocalTime.of(fin, 0)).build();
    }

    @Test
    void unTurnoTempranoChocaConElDeLaNocheAnteriorQueTodaviaNoTermina() {
        Turno nocheDelLunes = Turno.builder().id(UUID.randomUUID()).trabajador(mozo).fecha(martes.minusDays(1))
                .inicio(LocalTime.of(22, 0)).fin(LocalTime.of(6, 0)).build();
        when(trabajadorRepository.findById(mozoId)).thenReturn(Optional.of(mozo));
        when(turnoRepository.findByTrabajadorIdAndFechaBetweenOrderByFechaAscInicioAsc(eq(mozoId), any(), any()))
                .thenReturn(List.of(nocheDelLunes));

        assertThatThrownBy(() -> servicio.crear(turno(martes, 5, 13)))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("22:00 a 06:00");
        verify(turnoRepository, never()).save(any());
    }

    @Test
    void unTurnoDeCeroHorasNoSeGuarda() {
        when(trabajadorRepository.findById(mozoId)).thenReturn(Optional.of(mozo));

        assertThatThrownBy(() -> servicio.crear(turno(martes, 12, 12)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aQuienEstaDeBajaNoSeLeAsignanTurnos() {
        mozo.setActivo(false);
        when(trabajadorRepository.findById(mozoId)).thenReturn(Optional.of(mozo));

        assertThatThrownBy(() -> servicio.crear(turno(martes, 12, 20)))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("dado de baja");
    }
}
