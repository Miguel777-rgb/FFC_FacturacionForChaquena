package com.chaquena.backend_logistica.geo.service.impl;

import com.chaquena.backend_logistica.geo.dto.DireccionDto;
import com.chaquena.backend_logistica.geo.dto.EstadoGeoDto;
import com.chaquena.backend_logistica.geo.dto.RutaDto;
import com.chaquena.backend_logistica.geo.service.GeoService;
import com.chaquena.backend_logistica.local.dto.Coordenadas;
import com.chaquena.backend_logistica.local.service.DatosLocalService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * El mapa sobre OpenRouteService (https://openrouteservice.org): geocodificacion
 * inversa al tocar el mapa, sugerencias al escribir y ruta desde el local.
 *
 * <p>Se llama desde aqui y no desde el navegador para que la clave no viaje en
 * el bundle de un repositorio publico. Va en la cabecera {@code Authorization} y
 * no como {@code api_key} en la URL: las excepciones de E/S del cliente copian la
 * URL entera en su mensaje, y una clave en un log es una clave publicada.
 *
 * <p>Las respuestas se guardan en Redis. La cuota gratuita de ORS se mide por
 * dia, y el mozo que corrige un pin lo suelta tres veces en la misma esquina; la
 * direccion de un punto no cambia de un dia para otro. Si Redis falla, se sigue
 * sin cache.
 *
 * <p>Nada de lo que falle aqui llega como error a la pantalla: sin clave, sin red
 * o sin cuota, las respuestas vienen vacias y la direccion se escribe a mano.
 */
@Service
@Slf4j
public class GeoServiceImpl implements GeoService {

    private static final Duration VIDA_DIRECCION = Duration.ofDays(30);
    private static final Duration VIDA_SUGERENCIAS = Duration.ofDays(1);
    private static final Duration VIDA_RUTA = Duration.ofDays(1);

    /** Con menos letras las sugerencias son ruido y gastan cuota en cada tecla. */
    private static final int MINIMO_PARA_SUGERIR = 3;
    private static final int MAXIMO_DE_SUGERENCIAS = 5;

    private final RestClient ors;
    private final String apiKey;
    private final String pais;
    private final RedisTemplate<String, Object> redis;
    private final DatosLocalService datosLocal;

    @Autowired
    public GeoServiceImpl(
            @Value("${app.geo.ors-url}") String url,
            @Value("${app.geo.ors-api-key:}") String apiKey,
            @Value("${app.geo.pais:PE}") String pais,
            RedisTemplate<String, Object> redis,
            DatosLocalService datosLocal) {
        this(RestClient.builder().baseUrl(url).requestFactory(fabricaConPlazos()).build(),
                apiKey, pais, redis, datosLocal);
    }

    /** Para las pruebas, que le pasan un cliente atado a un servidor simulado. */
    GeoServiceImpl(RestClient ors, String apiKey, String pais,
            RedisTemplate<String, Object> redis, DatosLocalService datosLocal) {
        this.ors = ors;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.pais = pais;
        this.redis = redis;
        this.datosLocal = datosLocal;
    }

    /**
     * Un servicio externo lento no puede tener al mozo mirando un spinner: si ORS
     * no contesta en unos segundos, la direccion se escribe a mano.
     */
    private static ClientHttpRequestFactory fabricaConPlazos() {
        HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(Duration.ofSeconds(6));
        return fabrica;
    }

    @Override
    public EstadoGeoDto estado() {
        return new EstadoGeoDto(configurado());
    }

    @Override
    public DireccionDto direccionEn(double latitud, double longitud) {
        validar(latitud, longitud);
        // El pin se queda donde se toco: el punto lo eligio el mozo, y la calle
        // mas cercana solo le pone nombre.
        DireccionDto sinNombre = DireccionDto.builder().latitud(latitud).longitud(longitud).build();
        if (!configurado()) {
            return sinNombre;
        }

        String clave = "geo:direccion:" + redondeo(latitud) + ":" + redondeo(longitud);
        if (deCache(clave) instanceof DireccionDto guardada) {
            return guardada;
        }

        try {
            ColeccionOrs respuesta = ors.get()
                    .uri(u -> u.path("/geocode/reverse")
                            .queryParam("point.lat", latitud)
                            .queryParam("point.lon", longitud)
                            .queryParam("size", 1)
                            .queryParam("layers", "address,street,venue")
                            .queryParam("boundary.country", pais)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, apiKey)
                    .retrieve()
                    .body(ColeccionOrs.class);

            DireccionDto direccion = rasgos(respuesta).findFirst()
                    .map(r -> aDireccion(r, latitud, longitud))
                    .orElse(sinNombre);
            if (direccion.getDireccion() != null) {
                aCache(clave, direccion, VIDA_DIRECCION);
            }
            return direccion;
        } catch (RestClientException e) {
            avisar("geocodificacion inversa", e);
            return sinNombre;
        }
    }

    @Override
    public List<DireccionDto> sugerencias(String texto) {
        String limpio = texto == null ? "" : texto.trim().replaceAll("\\s+", " ");
        if (limpio.length() < MINIMO_PARA_SUGERIR || !configurado()) {
            return List.of();
        }

        Optional<Coordenadas> local = datosLocal.ubicacion();
        String clave = "geo:sugerencias:" + limpio.toLowerCase(Locale.ROOT)
                + local.map(c -> ":" + redondeo(c.latitud()) + ":" + redondeo(c.longitud())).orElse("");
        if (deCache(clave) instanceof List<?> guardadas) {
            return guardadas.stream()
                    .filter(DireccionDto.class::isInstance)
                    .map(DireccionDto.class::cast)
                    .toList();
        }

        try {
            ColeccionOrs respuesta = ors.get()
                    .uri(u -> {
                        // El texto va como variable y no pegado: asi se codifica
                        // entero, y un «&» en «Av. Arequipa & Jr. Lima» no parte
                        // el parametro en dos.
                        u.path("/geocode/autocomplete")
                                .queryParam("text", "{texto}")
                                .queryParam("boundary.country", pais);
                        // Con el local marcado, primero lo que esta cerca: «Jr. Lima»
                        // hay en todas las ciudades del Peru.
                        local.ifPresent(c -> u
                                .queryParam("focus.point.lat", c.latitud())
                                .queryParam("focus.point.lon", c.longitud()));
                        return u.build(limpio);
                    })
                    .header(HttpHeaders.AUTHORIZATION, apiKey)
                    .retrieve()
                    .body(ColeccionOrs.class);

            List<DireccionDto> encontradas = new ArrayList<>(rasgos(respuesta)
                    .filter(r -> r.geometry() != null && r.geometry().coordinates() != null
                            && r.geometry().coordinates().size() >= 2)
                    .limit(MAXIMO_DE_SUGERENCIAS)
                    .map(r -> aDireccion(r, r.geometry().coordinates().get(1), r.geometry().coordinates().get(0)))
                    .toList());
            aCache(clave, encontradas, VIDA_SUGERENCIAS);
            return encontradas;
        } catch (RestClientException e) {
            avisar("sugerencias de direccion", e);
            return List.of();
        }
    }

    @Override
    public RutaDto rutaDesdeElLocal(double latitud, double longitud) {
        validar(latitud, longitud);
        Optional<Coordenadas> local = datosLocal.ubicacion();
        if (local.isEmpty() || !configurado()) {
            return new RutaDto();
        }
        Coordenadas origen = local.get();

        String clave = "geo:ruta:" + redondeo(origen.latitud()) + ":" + redondeo(origen.longitud())
                + ":" + redondeo(latitud) + ":" + redondeo(longitud);
        if (deCache(clave) instanceof RutaDto guardada) {
            return guardada;
        }

        try {
            // ORS no tiene perfil de moto; el de auto es el que mas se le parece.
            RutaOrs respuesta = ors.get()
                    .uri(u -> u.path("/v2/directions/driving-car")
                            .queryParam("start", lonLat(origen.longitud(), origen.latitud()))
                            .queryParam("end", lonLat(longitud, latitud))
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, apiKey)
                    .retrieve()
                    .body(RutaOrs.class);

            RutaDto ruta = Optional.ofNullable(respuesta)
                    .map(RutaOrs::features)
                    .flatMap(f -> f.stream().findFirst())
                    .map(TramoOrs::properties)
                    .map(PropiedadesRutaOrs::summary)
                    .filter(r -> r.distance() != null && r.duration() != null)
                    .map(r -> new RutaDto((int) Math.round(r.distance()), (int) Math.round(r.duration())))
                    .orElseGet(RutaDto::new);
            if (ruta.getMetros() != null) {
                aCache(clave, ruta, VIDA_RUTA);
            }
            return ruta;
        } catch (RestClientException e) {
            avisar("ruta desde el local", e);
            return new RutaDto();
        }
    }

    // --- piezas -------------------------------------------------------------

    private boolean configurado() {
        return !apiKey.isEmpty();
    }

    private static void validar(double latitud, double longitud) {
        if (Double.isNaN(latitud) || latitud < -90 || latitud > 90) {
            throw new IllegalArgumentException("La latitud va de -90 a 90");
        }
        if (Double.isNaN(longitud) || longitud < -180 || longitud > 180) {
            throw new IllegalArgumentException("La longitud va de -180 a 180");
        }
    }

    /**
     * «Jr. Lima 452, Cercado de Lima»: calle y numero, y la zona que lo ubica,
     * que es como se dicta una direccion en el Peru. La etiqueta completa de ORS
     * trae ademas region y pais, que en un delivery del local sobran.
     */
    static DireccionDto aDireccion(RasgoOrs rasgo, double latitud, double longitud) {
        PropiedadesOrs p = rasgo.properties();
        if (p == null) {
            return DireccionDto.builder().latitud(latitud).longitud(longitud).build();
        }
        String calle = p.street() != null
                ? (p.housenumber() != null ? p.street() + " " + p.housenumber() : p.street())
                : p.name();
        String zona = Stream.of(p.neighbourhood(), p.locality(), p.county())
                .filter(z -> z != null && !z.isBlank())
                .findFirst()
                .orElse(null);

        String direccion;
        if (calle == null) {
            direccion = p.label();
        } else if (zona == null || calle.contains(zona)) {
            direccion = calle;
        } else {
            direccion = calle + ", " + zona;
        }
        return DireccionDto.builder()
                .direccion(direccion)
                .etiqueta(p.label() != null ? p.label() : direccion)
                .latitud(latitud)
                .longitud(longitud)
                .build();
    }

    private static Stream<RasgoOrs> rasgos(ColeccionOrs coleccion) {
        return coleccion == null || coleccion.features() == null
                ? Stream.empty()
                : coleccion.features().stream().filter(r -> r != null);
    }

    /** Cinco decimales son un metro: dos toques en el mismo portal comparten cache. */
    private static String redondeo(double grados) {
        return String.format(Locale.ROOT, "%.5f", grados);
    }

    private static String lonLat(double longitud, double latitud) {
        return String.format(Locale.ROOT, "%.6f,%.6f", longitud, latitud);
    }

    private Object deCache(String clave) {
        try {
            return redis.opsForValue().get(clave);
        } catch (RuntimeException e) {
            log.debug("Sin cache de geocodificacion: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private void aCache(String clave, Object valor, Duration vida) {
        try {
            redis.opsForValue().set(clave, valor, vida);
        } catch (RuntimeException e) {
            log.debug("No se pudo guardar en cache: {}", e.getClass().getSimpleName());
        }
    }

    /** Solo el tipo y el codigo: el mensaje de una excepcion de E/S trae la URL entera. */
    private static void avisar(String que, RestClientException e) {
        if (e instanceof RestClientResponseException respuesta) {
            log.warn("OpenRouteService respondio {} a la {}", respuesta.getStatusCode().value(), que);
        } else {
            log.warn("OpenRouteService no respondio a la {} ({})", que, e.getClass().getSimpleName());
        }
    }

    // --- lo que se lee de ORS -------------------------------------------------
    // Solo los campos que se usan; el resto se ignora para que un campo nuevo en
    // la respuesta no rompa la lectura.

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ColeccionOrs(List<RasgoOrs> features) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RasgoOrs(GeometriaOrs geometry, PropiedadesOrs properties) {
    }

    /** Un punto GeoJSON: [longitud, latitud], en ese orden. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeometriaOrs(List<Double> coordinates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PropiedadesOrs(String label, String name, String street, String housenumber,
            String neighbourhood, String locality, String county) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RutaOrs(List<TramoOrs> features) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TramoOrs(PropiedadesRutaOrs properties) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PropiedadesRutaOrs(ResumenOrs summary) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResumenOrs(Double distance, Double duration) {
    }
}
