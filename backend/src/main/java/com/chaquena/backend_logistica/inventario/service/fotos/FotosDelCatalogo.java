package com.chaquena.backend_logistica.inventario.service.fotos;

import com.chaquena.backend_logistica.archivos.service.ArchivoService;
import com.chaquena.backend_logistica.archivos.service.ComprobacionCarpetaArchivos;
import com.chaquena.backend_logistica.inventario.domain.Platillo;
import com.chaquena.backend_logistica.inventario.repository.PlatilloRepository;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Al arrancar, pone una foto del catalogo a los platillos que no tienen.
 *
 * <p>Las fotos van dentro de la aplicacion, en {@code carta/fotos/}, con su
 * autor y su licencia en {@code creditos.json} y en el README de esa carpeta.
 * Solo se miran los platillos sin foto: la que subio alguien manda y nunca se
 * reemplaza. Cada platillo recibe su propia copia, porque cambiarle la foto a
 * uno borra la anterior, y no puede llevarse por delante la de otro.
 */
@Slf4j
@Component
public class FotosDelCatalogo {

    static final String CARPETA = "carta/fotos/";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final PlatilloRepository platilloRepository;
    private final ArchivoService archivoService;
    private final ComprobacionCarpetaArchivos carpeta;
    private final TransactionOperations transacciones;

    public FotosDelCatalogo(PlatilloRepository platilloRepository, ArchivoService archivoService,
            ComprobacionCarpetaArchivos carpeta, TransactionOperations transacciones) {
        this.platilloRepository = platilloRepository;
        this.archivoService = archivoService;
        this.carpeta = carpeta;
        this.transacciones = transacciones;
    }

    /**
     * Despues de la migracion a WebP; los seeders ya corrieron, porque van antes
     * de que la aplicacion este lista. Un fallo aqui no impide arrancar: sin
     * foto, la tarjeta muestra el nombre.
     */
    @Order(2)
    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        try {
            asignar();
        } catch (RuntimeException e) {
            log.warn("No se pudieron poner las fotos del catalogo: {}", e.getMessage());
        }
    }

    private void asignar() {
        List<Platillo> sinFoto = platilloRepository.findByFotoIsNull();
        if (sinFoto.isEmpty()) {
            return;
        }
        if (!carpeta.sePuedeEscribir()) {
            log.warn("{} platillos siguen sin foto del catalogo: no se puede escribir en la carpeta.", sinFoto.size());
            return;
        }

        ReglaFotosCarta regla = new ReglaFotosCarta(leerCatalogo());
        Map<String, byte[]> leidas = new HashMap<>();
        List<String> fuera = new ArrayList<>();
        int puestas = 0;
        for (Platillo platillo : sinFoto) {
            Optional<String> foto = regla.fotoPara(platillo.getNombre());
            if (foto.isEmpty()) {
                fuera.add(platillo.getNombre());
            } else if (ponerFoto(platillo.getId(), foto.get(), leidas)) {
                puestas++;
            }
        }
        log.info("Fotos del catalogo: {} de {} platillos sin foto ya tienen una.{}", puestas, sinFoto.size(),
                fuera.isEmpty() ? "" : " No estan en el catalogo: " + String.join(", ", fuera) + ".");
    }

    /** Cada platillo en su transaccion: si uno falla, los demas siguen y el no queda a medias. */
    private boolean ponerFoto(UUID platilloId, String foto, Map<String, byte[]> leidas) {
        try {
            byte[] imagen = leidas.computeIfAbsent(foto, FotosDelCatalogo::leerFoto);
            return Boolean.TRUE.equals(transacciones.execute(estado -> {
                Platillo platillo = platilloRepository.findById(platilloId).orElse(null);
                // Alguien le subio una mientras tanto: esa manda.
                if (platillo == null || platillo.getFoto() != null) {
                    return false;
                }
                platillo.setFoto(archivoService.guardar(imagen, foto + ".webp"));
                platillo.setModifiedBy(UsuarioActual.SISTEMA);
                platilloRepository.save(platillo);
                return true;
            }));
        } catch (RuntimeException e) {
            log.warn("No se pudo poner la foto {} al platillo {}: {}", foto, platilloId, e.getMessage());
            return false;
        }
    }

    /** Cada foto con los nombres de la carta que la usan, en el orden del fichero. */
    static Map<String, List<String>> leerCatalogo() {
        try (InputStream entrada = new ClassPathResource(CARPETA + "catalogo.json").getInputStream()) {
            return JSON.readValue(entrada, new TypeReference<LinkedHashMap<String, List<String>>>() {
            });
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el catalogo de fotos.", e);
        }
    }

    static byte[] leerFoto(String foto) {
        try (InputStream entrada = new ClassPathResource(CARPETA + foto + ".webp").getInputStream()) {
            return entrada.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se encontro la foto " + foto + ".webp del catalogo.", e);
        }
    }
}
