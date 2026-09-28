package com.chaquena.backend_logistica.shared.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Las reglas del seeder que se pueden comprobar sin base de datos. */
class HistorialDemoSeederTest {

    /**
     * El historial escribe con JDBC, sin los @PrePersist que rellenan las
     * entidades. En local muchas columnas NOT NULL tienen DEFAULT (lo pusieron
     * las migraciones a mano), pero en produccion la base la creo Hibernate y no
     * los tiene: un cupon sin `monto_descuento` dejo el backend en un bucle de
     * reinicios. Por eso cada insercion tiene que nombrar todas las columnas NOT
     * NULL, con DEFAULT o sin el. El id solo se exime cuando es de identidad.
     */
    @Test
    void cadaInsercionEscribeTodasLasColumnasNotNullSinFiarseDeLosDefault() throws IOException {
        String esquema = Files.readString(Path.of("../bd/schema.sql"));
        Pattern insert = Pattern.compile("insert into (\\w+) \\((.*?)\\)\\s*values", Pattern.DOTALL);

        for (String sql : HistorialDemoSeeder.inserciones()) {
            Matcher m = insert.matcher(sql);
            assertThat(m.find()).as(sql).isTrue();
            String tabla = m.group(1);
            Set<String> escritas = Arrays.stream(m.group(2).split(","))
                    .map(String::trim).collect(Collectors.toSet());

            boolean idDeIdentidad = esquema.contains(
                    "ALTER TABLE public." + tabla + " ALTER COLUMN id ADD GENERATED");
            List<String> faltan = columnasNotNull(esquema, tabla).stream()
                    .filter(c -> !escritas.contains(c))
                    .filter(c -> !(c.equals("id") && idDeIdentidad))
                    .toList();

            assertThat(faltan).as("columnas NOT NULL que no escribe el insert de " + tabla).isEmpty();
        }
    }

    private static List<String> columnasNotNull(String esquema, String tabla) {
        Matcher m = Pattern.compile("CREATE TABLE public\\." + tabla + " \\((.*?)\\n\\);", Pattern.DOTALL)
                .matcher(esquema);
        assertThat(m.find()).as("la tabla " + tabla + " en bd/schema.sql").isTrue();
        return Arrays.stream(m.group(1).split("\n"))
                .map(String::trim)
                .filter(l -> !l.isEmpty() && !l.startsWith("CONSTRAINT") && l.contains("NOT NULL"))
                .map(l -> l.split("\\s+")[0])
                .toList();
    }

    @Test
    void enSesentaDiasHayDosDiasConFraude() {
        LocalDate hoy = LocalDate.of(2026, 9, 28);
        long conFraude = hoy.minusDays(60).datesUntil(hoy.plusDays(1))
                .filter(HistorialDemoSeeder::diaConFraude).count();

        assertThat(conFraude).isBetween(2L, 3L);
    }

    @Test
    void elLomoSePideMasQueUnPlatoNuevoConTildesOSin() {
        assertThat(HistorialDemoSeeder.pesoDe("Lomo Saltado")).isEqualTo(10);
        assertThat(HistorialDemoSeeder.pesoDe("Ají de Gallina")).isEqualTo(HistorialDemoSeeder.pesoDe("Aji de Gallina"));
        assertThat(HistorialDemoSeeder.pesoDe("Papa a la Huancaína")).isEqualTo(5);
        assertThat(HistorialDemoSeeder.pesoDe("Tacu tacu")).isEqualTo(3);
    }
}
