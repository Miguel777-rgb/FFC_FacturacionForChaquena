package com.chaquena.backend_logistica.delivery.bot.parser;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class CantidadParserService {

    private static final Map<String, Integer> NUMEROS_TEXTO = Map.of(
            "uno", 1, "dos", 2, "tres", 3, "cuatro", 4, "cinco", 5,
            "seis", 6, "siete", 7, "ocho", 8, "nueve", 9, "diez", 10
    );

    public Optional<Integer> parsearCantidad(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        String limpio = texto.trim().toLowerCase();
        try {
            return Optional.of(Integer.parseInt(limpio));
        } catch (NumberFormatException e) {
            try {
                double val = Double.parseDouble(limpio);
                return Optional.of((int) Math.round(val));
            } catch (NumberFormatException ex) {
                if (NUMEROS_TEXTO.containsKey(limpio)) {
                    return Optional.of(NUMEROS_TEXTO.get(limpio));
                }
            }
        }
        return Optional.empty();
    }
}
