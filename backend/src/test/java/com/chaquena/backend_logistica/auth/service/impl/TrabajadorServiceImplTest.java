package com.chaquena.backend_logistica.auth.service.impl;

import com.chaquena.backend_logistica.auth.domain.Cargo;
import com.chaquena.backend_logistica.auth.domain.CargoRol;
import com.chaquena.backend_logistica.auth.domain.Rol;
import com.chaquena.backend_logistica.auth.domain.Trabajador;
import com.chaquena.backend_logistica.auth.dto.BootstrapAdminRequestDto;
import com.chaquena.backend_logistica.auth.dto.TrabajadorResponseDto;
import com.chaquena.backend_logistica.auth.repository.CargoRepository;
import com.chaquena.backend_logistica.auth.repository.RolRepository;
import com.chaquena.backend_logistica.auth.repository.TrabajadorRepository;
import com.chaquena.backend_logistica.auth.service.TrabajadorContexto;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La puerta del primer administrador. El arranque siembra {@code bot.clientes} en
 * toda instalacion, asi que una guarda que contara filas la dejaria cerrada antes
 * de que nadie pudiera usarla.
 */
@ExtendWith(MockitoExtension.class)
class TrabajadorServiceImplTest {

    @Mock
    private TrabajadorRepository trabajadorRepository;

    @Mock
    private CargoRepository cargoRepository;

    @Mock
    private RolRepository rolRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private TrabajadorServiceImpl servicio;

    private final BootstrapAdminRequestDto pedido = BootstrapAdminRequestDto.builder()
            .dni("40000001").nombres("Ana").apellidos("Quispe").correo("ana@chaquena.pe")
            .celular("+51 987 654 321").username("ana").password("Secreta2026").build();

    @Test
    void conSoloElTrabajadorDelBotElPrimerAdminSeCrea() {
        Rol admin = Rol.builder().nombre("ADMIN").build();
        Cargo cargo = Cargo.builder().nombre("ADMINISTRADOR").build();
        cargo.getCargoRoles().add(CargoRol.builder().cargo(cargo).rol(admin).build());

        when(trabajadorRepository.existsByUsernameNot(TrabajadorContexto.USERNAME_BOT_CLIENTES))
                .thenReturn(false);
        when(cargoRepository.findByNombreIgnoreCase("ADMINISTRADOR")).thenReturn(Optional.of(cargo));
        when(rolRepository.findByNombreIgnoreCase("ADMIN")).thenReturn(Optional.of(admin));
        when(passwordEncoder.encode("Secreta2026")).thenReturn("hash");
        when(trabajadorRepository.save(any(Trabajador.class))).thenAnswer(i -> i.getArgument(0));

        TrabajadorResponseDto creado = servicio.bootstrapPrimerAdmin(pedido);

        assertThat(creado.getUsername()).isEqualTo("ana");
        assertThat(creado.getCargoNombre()).isEqualTo("ADMINISTRADOR");
        assertThat(creado.getCelular()).isEqualTo("51987654321");
    }

    @Test
    void conCualquierOtroTrabajadorLaPuertaSeCierra() {
        when(trabajadorRepository.existsByUsernameNot(TrabajadorContexto.USERNAME_BOT_CLIENTES))
                .thenReturn(true);

        assertThatThrownBy(() -> servicio.bootstrapPrimerAdmin(pedido))
                .isInstanceOf(ConflictoException.class);
        verify(trabajadorRepository, never()).save(any());
    }
}
