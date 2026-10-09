package com.chaquena.backend_logistica.inventario.service.fotos;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReglaFotosCartaTest {

    @Test
    void unNombreValeSiApareceEnteroPalabraPorPalabra() {
        ReglaFotosCarta regla = new ReglaFotosCarta(Map.of(
                "arroz-chaufa", List.of("Arroz Chaufa"),
                "infusion", List.of("Anís")));

        assertThat(regla.fotoPara("Arroz Chaufa de Pollo")).contains("arroz-chaufa");
        assertThat(regla.fotoPara("ARROZ CHAUFA (personal)")).contains("arroz-chaufa");
        assertThat(regla.fotoPara("Anis")).contains("infusion");
        assertThat(regla.fotoPara("Anisado")).isEmpty();
        assertThat(regla.fotoPara("Chaufa")).isEmpty();
    }

    @Test
    void siValenVariosGanaElMasLargo() {
        ReglaFotosCarta regla = new ReglaFotosCarta(Map.of(
                "lomo-saltado", List.of("Lomo Saltado"),
                "bistec-a-lo-pobre", List.of("Lomo Saltado a lo Pobre")));

        assertThat(regla.fotoPara("Lomo Saltado a lo Pobre")).contains("bistec-a-lo-pobre");
        assertThat(regla.fotoPara("Lomo Saltado")).contains("lomo-saltado");
    }

    @Test
    void aIgualLargoDecideElOrdenDelCatalogo() {
        Map<String, List<String>> catalogo = new LinkedHashMap<>();
        catalogo.put("arroz-chaufa", List.of("Arroz Chaufa"));
        catalogo.put("chaufa-mixto", List.of("Chaufa Mixto"));

        assertThat(new ReglaFotosCarta(catalogo).fotoPara("Arroz Chaufa Mixto")).contains("arroz-chaufa");
    }

    @Test
    void conIgualDelanteTieneQueSerElNombreEntero() {
        ReglaFotosCarta regla = new ReglaFotosCarta(Map.of(
                "cafe", List.of("=Café"),
                "cafe-con-leche", List.of("Café con Leche")));

        assertThat(regla.fotoPara("café ")).contains("cafe");
        assertThat(regla.fotoPara("Café con Leche")).contains("cafe-con-leche");
        assertThat(regla.fotoPara("Café Pasado")).isEmpty();
    }

    @Test
    void sinNombreQueValgaNoHayFoto() {
        ReglaFotosCarta regla = new ReglaFotosCarta(Map.of("ceviche", List.of("Ceviche")));

        assertThat(regla.fotoPara("Pachamanca")).isEmpty();
        assertThat(regla.fotoPara("")).isEmpty();
        assertThat(regla.fotoPara(null)).isEmpty();
    }

    /** Los seis de la demo y algunos de la carta impresa, contra el catalogo de verdad. */
    @Test
    void conElCatalogoRealCadaPlatilloTieneLaFotoQueLeToca() {
        ReglaFotosCarta regla = new ReglaFotosCarta(FotosDelCatalogo.leerCatalogo());

        assertThat(regla.fotoPara("Lomo Saltado")).contains("lomo-saltado");
        assertThat(regla.fotoPara("Ceviche Clásico")).contains("ceviche");
        assertThat(regla.fotoPara("Ají de Gallina")).contains("aji-de-gallina");
        assertThat(regla.fotoPara("Arroz Chaufa de Pollo")).contains("arroz-chaufa");
        assertThat(regla.fotoPara("Papa a la Huancaína")).contains("papa-huancaina");
        assertThat(regla.fotoPara("Chicharrón de Pescado")).contains("chicharron-pescado");
        assertThat(regla.fotoPara("Lomo Saltado a lo Pobre")).contains("bistec-a-lo-pobre");
        assertThat(regla.fotoPara("Porción de Arroz Chaufa")).contains("arroz-chaufa");
        assertThat(regla.fotoPara("Café")).contains("cafe");
        assertThat(regla.fotoPara("Café con Leche")).contains("cafe-con-leche");
    }
}
