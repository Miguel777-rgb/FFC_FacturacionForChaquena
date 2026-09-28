package com.chaquena.backend_logistica.geo.service.impl;

import com.chaquena.backend_logistica.geo.dto.DireccionDto;
import com.chaquena.backend_logistica.geo.dto.RutaDto;
import com.chaquena.backend_logistica.local.dto.Coordenadas;
import com.chaquena.backend_logistica.local.service.DatosLocalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * El geocodificador se prueba contra un ORS simulado con respuestas de la forma
 * real (GeoJSON). Lo que importa: como se compone la direccion que ve el mozo,
 * que la clave nunca vaya en la URL, y que un ORS caido no rompa el delivery.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GeoServiceImplTest {

    private static final String CLAVE = "clave-de-prueba";

    @Mock
    private RedisTemplate<String, Object> redis;

    @Mock
    private ValueOperations<String, Object> valores;

    @Mock
    private DatosLocalService datosLocal;

    private MockRestServiceServer ors;
    private GeoServiceImpl servicio;

    @BeforeEach
    void preparar() {
        when(redis.opsForValue()).thenReturn(valores);
        when(datosLocal.ubicacion()).thenReturn(Optional.of(new Coordenadas(-12.0464, -77.0428)));
        servicio = conClave(CLAVE);
    }

    private GeoServiceImpl conClave(String clave) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ors.test");
        ors = MockRestServiceServer.bindTo(builder).build();
        return new GeoServiceImpl(builder.build(), clave, "PE", redis, datosLocal);
    }

    private static final String INVERSA = """
            {"type":"FeatureCollection","features":[{"type":"Feature",
              "geometry":{"type":"Point","coordinates":[-77.04291,-12.04652]},
              "properties":{"label":"Jirón Lima 452, Lima, LM, Peru","name":"Jirón Lima 452",
                "housenumber":"452","street":"Jirón Lima","neighbourhood":"Cercado de Lima",
                "locality":"Lima","region":"Lima","country":"Peru","confidence":0.9}}]}
            """;

    @Test
    void alTocarElMapaCompletaCalleNumeroYZonaConLaClaveEnLaCabecera() {
        ors.expect(requestTo(startsWith("https://ors.test/geocode/reverse")))
                .andExpect(header("Authorization", CLAVE))
                .andExpect(requestTo(not(containsString(CLAVE))))
                .andExpect(queryParam("point.lat", "-12.0465"))
                .andExpect(queryParam("boundary.country", "PE"))
                .andRespond(withSuccess(INVERSA, MediaType.APPLICATION_JSON));

        DireccionDto direccion = servicio.direccionEn(-12.0465, -77.0429);

        assertThat(direccion.getDireccion()).isEqualTo("Jirón Lima 452, Cercado de Lima");
        assertThat(direccion.getEtiqueta()).isEqualTo("Jirón Lima 452, Lima, LM, Peru");
        // El pin se queda donde se toco, no donde ORS ubica la calle.
        assertThat(direccion.getLatitud()).isEqualTo(-12.0465);
        assertThat(direccion.getLongitud()).isEqualTo(-77.0429);
        verify(valores).set(eq("geo:direccion:-12.04650:-77.04290"), any(), any(Duration.class));
        ors.verify();
    }

    @Test
    void unPuntoYaConsultadoSaleDeLaCacheSinGastarCuota() {
        DireccionDto guardada = DireccionDto.builder().direccion("Jr. Lima 452").build();
        when(valores.get("geo:direccion:-12.04650:-77.04290")).thenReturn(guardada);

        assertThat(servicio.direccionEn(-12.0465, -77.0429)).isSameAs(guardada);
        ors.verify();
    }

    @Test
    void sinClaveElPuntoSirveAunqueNoTengaNombre() {
        GeoServiceImpl sinClave = conClave("  ");

        DireccionDto direccion = sinClave.direccionEn(-12.0465, -77.0429);

        assertThat(sinClave.estado().getGeocodificacion()).isFalse();
        assertThat(direccion.getDireccion()).isNull();
        assertThat(direccion.getLatitud()).isEqualTo(-12.0465);
        assertThat(sinClave.sugerencias("Jiron Lima")).isEmpty();
        ors.verify();
    }

    @Test
    void siOrsFallaLaDireccionSeEscribeAManoSinError() {
        ors.expect(requestTo(startsWith("https://ors.test/geocode/reverse")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        DireccionDto direccion = servicio.direccionEn(-12.0465, -77.0429);

        assertThat(direccion.getDireccion()).isNull();
        verify(valores, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    void lasSugerenciasPidenLoCercanoAlLocalYLeenLongitudLatitud() {
        ors.expect(requestTo(startsWith("https://ors.test/geocode/autocomplete")))
                .andExpect(queryParam("text", "Jiron%20Lima%20%26%20Av.%20Abancay"))
                .andExpect(queryParam("focus.point.lat", "-12.0464"))
                .andExpect(queryParam("focus.point.lon", "-77.0428"))
                .andRespond(withSuccess(INVERSA, MediaType.APPLICATION_JSON));

        List<DireccionDto> sugerencias = servicio.sugerencias("  Jiron Lima & Av. Abancay ");

        assertThat(sugerencias).singleElement().satisfies(s -> {
            assertThat(s.getDireccion()).isEqualTo("Jirón Lima 452, Cercado de Lima");
            // GeoJSON va [longitud, latitud]: aqui se dan vuelta.
            assertThat(s.getLatitud()).isEqualTo(-12.04652);
            assertThat(s.getLongitud()).isEqualTo(-77.04291);
        });
        ors.verify();
    }

    @Test
    void conMenosDeTresLetrasNoSeSugiereNada() {
        assertThat(servicio.sugerencias("Jr")).isEmpty();
        ors.verify();
    }

    @Test
    void laRutaSaleDelLocalEnMetrosYSegundos() {
        ors.expect(requestTo(startsWith("https://ors.test/v2/directions/driving-car")))
                .andExpect(queryParam("start", "-77.042800,-12.046400"))
                .andExpect(queryParam("end", "-77.030000,-12.100000"))
                .andRespond(withSuccess("""
                        {"type":"FeatureCollection","features":[{"type":"Feature",
                          "properties":{"summary":{"distance":3210.6,"duration":734.4}},
                          "geometry":{"type":"LineString","coordinates":[[-77.0428,-12.0464],[-77.03,-12.1]]}}]}
                        """, MediaType.parseMediaType("application/geo+json")));

        RutaDto ruta = servicio.rutaDesdeElLocal(-12.1, -77.03);

        assertThat(ruta.getMetros()).isEqualTo(3211);
        assertThat(ruta.getSegundos()).isEqualTo(734);
        ors.verify();
    }

    @Test
    void sinElLocalMarcadoNoHayRuta() {
        when(datosLocal.ubicacion()).thenReturn(Optional.empty());

        RutaDto ruta = servicio.rutaDesdeElLocal(-12.1, -77.03);

        assertThat(ruta.getMetros()).isNull();
        ors.verify();
    }

    @Test
    void unPuntoFueraDelMundoSeRechaza() {
        assertThatThrownBy(() -> servicio.direccionEn(-120, -77))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.rutaDesdeElLocal(-12, 200))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
