package com.chaquena.backend_logistica.inventario.service.impl;

import com.chaquena.backend_logistica.archivos.service.ArchivoService;
import com.chaquena.backend_logistica.inventario.domain.*;
import com.chaquena.backend_logistica.inventario.dto.*;
import com.chaquena.backend_logistica.inventario.repository.*;
import com.chaquena.backend_logistica.inventario.service.PlatilloService;
import com.chaquena.backend_logistica.inventario.service.ReglaCosto;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PlatilloServiceImpl implements PlatilloService {

    private final PlatilloRepository platilloRepository;
    private final CategoriaPlatilloRepository categoriaRepository;
    private final InsumoRepository insumoRepository;
    private final AlergenoRepository alergenoRepository;
    private final ArchivoService archivoService;
    private final CostoPlatillos costoPlatillos;

    @Override
    @Transactional
    public PlatilloResponseDto crear(PlatilloRequestDto request) {
        Platillo platillo = Platillo.builder()
                .categoria(buscarCategoria(request.getCategoriaId()))
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .precioVentaBase(request.getPrecioVentaBase())
                .activo(request.getActivo() == null || request.getActivo())
                .foto(request.getFotoId() != null ? archivoService.obtener(request.getFotoId()) : null)
                .tiempoPreparacionMinutos(request.getTiempoPreparacionMinutos())
                .createdBy(UsuarioActual.username())
                .build();
        if (request.getAlergenoIds() != null) {
            platillo.setAlergenos(alergenos(request.getAlergenoIds()));
        }
        return respuesta(platilloRepository.save(platillo));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<PlatilloResponseDto> buscar(Integer categoriaId, Boolean activo, String termino,
            Pageable pageable) {
        Page<Platillo> pagina = platilloRepository.buscar(categoriaId, activo, patron(termino), pageable);
        Map<UUID, ReglaCosto.Costo> costos = costoPlatillos.de(pagina.getContent());
        return PageResponseDto.de(pagina, p -> PlatilloResponseDto.fromEntity(p, costos.get(p.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public PlatilloResponseDto obtenerPorId(UUID id) {
        Platillo platillo = buscarConReceta(id);
        return PlatilloResponseDto.conReceta(platillo, costoDe(platillo));
    }

    /**
     * El PUT reemplaza: una foto o un tiempo que no llegan se quitan. Los
     * alergenos son la excepcion, igual que el horario del local: sin la lista
     * no se tocan, y con una lista vacia se quitan todos.
     */
    @Override
    @Transactional
    public PlatilloResponseDto actualizar(UUID id, PlatilloRequestDto request) {
        Platillo platillo = buscar(id);
        platillo.setCategoria(buscarCategoria(request.getCategoriaId()));
        platillo.setNombre(request.getNombre());
        platillo.setDescripcion(request.getDescripcion());
        platillo.setPrecioVentaBase(request.getPrecioVentaBase());
        if (request.getActivo() != null) {
            platillo.setActivo(request.getActivo());
        }
        platillo.setTiempoPreparacionMinutos(request.getTiempoPreparacionMinutos());
        if (request.getAlergenoIds() != null) {
            platillo.getAlergenos().clear();
            platillo.getAlergenos().addAll(alergenos(request.getAlergenoIds()));
        }

        UUID fotoAnterior = platillo.getFoto() != null ? platillo.getFoto().getId() : null;
        boolean cambiaFoto = !Objects.equals(fotoAnterior, request.getFotoId());
        if (cambiaFoto) {
            platillo.setFoto(request.getFotoId() != null ? archivoService.obtener(request.getFotoId()) : null);
        }

        platillo.setModifiedBy(UsuarioActual.username());
        Platillo guardado = platilloRepository.save(platillo);

        // La foto reemplazada no la usa nadie mas: se borra para que el disco no
        // crezca con cada cambio de foto.
        if (cambiaFoto && fotoAnterior != null) {
            archivoService.eliminar(fotoAnterior);
        }
        return respuesta(guardado);
    }

    @Override
    @Transactional
    public PlatilloResponseDto cambiarActivo(UUID id, boolean activo) {
        Platillo platillo = buscar(id);
        platillo.setActivo(activo);
        platillo.setModifiedBy(UsuarioActual.username());
        return respuesta(platilloRepository.save(platillo));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecetaItemDto> obtenerReceta(UUID id) {
        Platillo platillo = buscarConReceta(id);
        return platillo.getReceta().stream().map(RecetaItemDto::fromEntity).toList();
    }

    /**
     * Reemplaza la receta completa en una sola transaccion. Se hace por
     * sustitucion y no por diferencias para que el BOM del platillo sea
     * siempre exactamente lo que envio el administrador.
     */
    @Override
    @Transactional
    public List<RecetaItemDto> reemplazarReceta(UUID id, RecetaRequestDto request) {
        Platillo platillo = buscarConReceta(id);
        platillo.getReceta().clear();

        for (RecetaItemDto item : request.getInsumos()) {
            Insumo insumo = insumoRepository.findById(item.getInsumoId())
                    .orElseThrow(() -> RecursoNoEncontradoException.de("el insumo", item.getInsumoId()));
            InsumoPlatillo linea = InsumoPlatillo.builder()
                    .platillo(platillo)
                    .insumo(insumo)
                    .cantidadRequerida(item.getCantidadRequerida())
                    .createdBy(UsuarioActual.username())
                    .build();
            platillo.getReceta().add(linea);
        }

        platillo.setModifiedBy(UsuarioActual.username());
        Platillo guardado = platilloRepository.save(platillo);
        return guardado.getReceta().stream().map(RecetaItemDto::fromEntity).toList();
    }

    /**
     * Carta con disponibilidad resuelta: para cada platillo activo calcula
     * cuantas porciones alcanzan los insumos, tomando el insumo mas escaso de
     * la receta como limite. Un platillo sin receta se considera siempre
     * disponible (bebidas embotelladas, por ejemplo).
     */
    @Override
    @Transactional(readOnly = true)
    public List<PlatilloDisponibleDto> menuDisponible() {
        List<Platillo> platillos = platilloRepository.findByActivoTrue();
        List<PlatilloDisponibleDto> resultado = new ArrayList<>();

        for (Platillo platillo : platillos) {
            List<InsumoPlatillo> receta = platillo.getReceta();
            Integer porciones = null;
            List<String> faltantes = new ArrayList<>();

            if (receta != null && !receta.isEmpty()) {
                porciones = Integer.MAX_VALUE;
                for (InsumoPlatillo linea : receta) {
                    BigDecimal requerida = linea.getCantidadRequerida();
                    if (requerida == null || requerida.signum() <= 0) {
                        continue;
                    }
                    BigDecimal stock = linea.getInsumo().getStockActual() != null
                            ? linea.getInsumo().getStockActual()
                            : BigDecimal.ZERO;
                    int posibles = stock.divide(requerida, 0, RoundingMode.DOWN).intValue();
                    porciones = Math.min(porciones, Math.max(posibles, 0));
                    if (posibles <= 0) {
                        faltantes.add(linea.getInsumo().getNombre());
                    }
                }
                if (porciones == Integer.MAX_VALUE) {
                    porciones = null;
                }
            }

            resultado.add(PlatilloDisponibleDto.builder()
                    .id(platillo.getId())
                    .nombre(platillo.getNombre())
                    .descripcion(platillo.getDescripcion())
                    .categoriaId(platillo.getCategoria() != null ? platillo.getCategoria().getId() : null)
                    .categoriaNombre(platillo.getCategoria() != null ? platillo.getCategoria().getNombre() : null)
                    .precioVentaBase(platillo.getPrecioVentaBase())
                    .porcionesPosibles(porciones)
                    .disponible(porciones == null || porciones > 0)
                    .insumosFaltantes(faltantes)
                    .fotoId(platillo.getFoto() != null ? platillo.getFoto().getId() : null)
                    .tiempoPreparacionMinutos(platillo.getTiempoPreparacionMinutos())
                    .alergenos(Alergeno.nombresDe(platillo.getAlergenos()))
                    .build());
        }
        return resultado;
    }

    private PlatilloResponseDto respuesta(Platillo platillo) {
        return PlatilloResponseDto.fromEntity(platillo, costoDe(platillo));
    }

    private ReglaCosto.Costo costoDe(Platillo platillo) {
        return costoPlatillos.de(List.of(platillo)).get(platillo.getId());
    }

    /** Todos o ninguno: un id que no existe no deja el platillo con la mitad de sus alergenos. */
    private Set<Alergeno> alergenos(List<Integer> ids) {
        Set<Integer> pedidos = new LinkedHashSet<>(ids);
        List<Alergeno> encontrados = alergenoRepository.findAllById(pedidos);
        if (encontrados.size() != pedidos.size()) {
            Set<Integer> faltan = new LinkedHashSet<>(pedidos);
            encontrados.forEach(a -> faltan.remove(a.getId()));
            throw RecursoNoEncontradoException.de("el alergeno", faltan.iterator().next());
        }
        return new HashSet<>(encontrados);
    }

    /** Convierte el texto libre en el patron LIKE que espera el repositorio. */
    private String patron(String termino) {
        return (termino == null || termino.isBlank())
                ? "%"
                : "%" + termino.trim().toLowerCase() + "%";
    }

    private Platillo buscar(UUID id) {
        return platilloRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el platillo", id));
    }

    private Platillo buscarConReceta(UUID id) {
        return platilloRepository.findWithRecetaById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el platillo", id));
    }

    private CategoriaPlatillo buscarCategoria(Integer id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la categoria", id));
    }
}
