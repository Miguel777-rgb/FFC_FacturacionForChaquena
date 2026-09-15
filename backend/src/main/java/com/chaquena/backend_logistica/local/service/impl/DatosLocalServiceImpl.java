package com.chaquena.backend_logistica.local.service.impl;

import com.chaquena.backend_logistica.archivos.service.ArchivoService;
import com.chaquena.backend_logistica.local.domain.DatosLocal;
import com.chaquena.backend_logistica.local.domain.HorarioLocal;
import com.chaquena.backend_logistica.local.dto.DatosLocalDto;
import com.chaquena.backend_logistica.local.dto.HorarioLocalDto;
import com.chaquena.backend_logistica.local.repository.DatosLocalRepository;
import com.chaquena.backend_logistica.local.repository.HorarioLocalRepository;
import com.chaquena.backend_logistica.local.service.DatosLocalService;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DatosLocalServiceImpl implements DatosLocalService {

    private static final Locale ESPANOL = Locale.of("es");

    private final DatosLocalRepository datosRepository;
    private final HorarioLocalRepository horarioRepository;
    private final ArchivoService archivoService;

    @Override
    @Transactional
    public DatosLocalDto obtener() {
        return DatosLocalDto.de(datos(), horarios());
    }

    /**
     * Se valida todo el horario antes de escribir nada: un dia mal cargado no
     * puede dejar guardados los datos y medio horario.
     */
    @Override
    @Transactional
    public DatosLocalDto actualizar(DatosLocalDto cambios) {
        if (cambios.getHorarios() != null) {
            validarHorarios(cambios.getHorarios());
        }

        String autor = UsuarioActual.username();

        DatosLocal datos = datos();
        datos.setNombreComercial(limpio(cambios.getNombreComercial()));
        datos.setRuc(limpio(cambios.getRuc()));
        datos.setDireccion(limpio(cambios.getDireccion()));
        datos.setTelefono(limpio(cambios.getTelefono()));
        datos.setCorreo(limpio(cambios.getCorreo()));
        if (cambios.getPorcentajeIgv() != null) {
            datos.setPorcentajeIgv(cambios.getPorcentajeIgv().setScale(2, RoundingMode.HALF_UP));
        }
        datos.setModifiedBy(autor);
        datosRepository.save(datos);

        if (cambios.getHorarios() != null) {
            Map<DayOfWeek, HorarioLocal> porDia = horarios().stream()
                    .collect(Collectors.toMap(HorarioLocal::getDia, Function.identity()));
            for (HorarioLocalDto h : cambios.getHorarios()) {
                HorarioLocal fila = porDia.get(h.getDia());
                boolean cerrado = Boolean.TRUE.equals(h.getCerrado());
                fila.setCerrado(cerrado);
                fila.setAbre(cerrado ? null : h.getAbre());
                fila.setCierra(cerrado ? null : h.getCierra());
                fila.setModifiedBy(autor);
            }
            horarioRepository.saveAll(porDia.values());
        }

        return obtener();
    }

    /** El logo que se reemplaza no lo usa nadie mas: se borra al confirmar el cambio. */
    @Override
    @Transactional
    public DatosLocalDto cambiarLogo(UUID archivoId) {
        DatosLocal datos = datos();
        UUID anterior = datos.getLogo() != null ? datos.getLogo().getId() : null;
        if (archivoId.equals(anterior)) {
            return obtener();
        }

        datos.setLogo(archivoService.obtener(archivoId));
        datos.setModifiedBy(UsuarioActual.username());
        datosRepository.save(datos);
        if (anterior != null) {
            archivoService.eliminar(anterior);
        }
        return obtener();
    }

    @Override
    @Transactional
    public DatosLocalDto quitarLogo() {
        DatosLocal datos = datos();
        if (datos.getLogo() == null) {
            return obtener();
        }

        UUID anterior = datos.getLogo().getId();
        datos.setLogo(null);
        datos.setModifiedBy(UsuarioActual.username());
        datosRepository.save(datos);
        archivoService.eliminar(anterior);
        return obtener();
    }

    @Override
    @Transactional
    public BigDecimal porcentajeIgv() {
        BigDecimal porcentaje = datos().getPorcentajeIgv();
        return porcentaje != null ? porcentaje : DatosLocal.PORCENTAJE_IGV_POR_DEFECTO;
    }

    private DatosLocal datos() {
        return datosRepository.findById(DatosLocal.ID_UNICO)
                .orElseGet(() -> datosRepository.save(DatosLocal.porDefecto()));
    }

    /** Los siete dias, de lunes a domingo. Los que falten se crean sin horario definido. */
    private List<HorarioLocal> horarios() {
        List<HorarioLocal> todos = new ArrayList<>(horarioRepository.findAll());
        Set<DayOfWeek> presentes = todos.stream().map(HorarioLocal::getDia).collect(Collectors.toSet());

        List<HorarioLocal> faltantes = Arrays.stream(DayOfWeek.values())
                .filter(dia -> !presentes.contains(dia))
                .map(HorarioLocal::sinDefinir)
                .toList();
        if (!faltantes.isEmpty()) {
            todos.addAll(horarioRepository.saveAll(faltantes));
        }

        todos.sort(Comparator.comparing(HorarioLocal::getDia));
        return todos;
    }

    private void validarHorarios(List<HorarioLocalDto> horarios) {
        Set<DayOfWeek> vistos = EnumSet.noneOf(DayOfWeek.class);
        for (HorarioLocalDto h : horarios) {
            if (h.getDia() == null) {
                throw new IllegalArgumentException("Cada horario necesita su dia.");
            }
            String dia = h.getDia().getDisplayName(TextStyle.FULL, ESPANOL);
            if (!vistos.add(h.getDia())) {
                throw new IllegalArgumentException("El " + dia + " aparece dos veces en el horario.");
            }
            if (Boolean.TRUE.equals(h.getCerrado())) {
                continue;
            }
            if ((h.getAbre() == null) != (h.getCierra() == null)) {
                throw new IllegalArgumentException("El " + dia
                        + " tiene hora de apertura o de cierre, pero no las dos.");
            }
            if (h.getAbre() != null && h.getAbre().equals(h.getCierra())) {
                throw new IllegalArgumentException("El " + dia + " abre y cierra a la misma hora.");
            }
        }
    }

    private String limpio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
