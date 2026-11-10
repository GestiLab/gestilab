package com.tedesoft.inventarioservice.domain.entities;

import com.tedesoft.inventarioservice.domain.exception.ReglaNegocioException;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Estados posibles de una unidad (RF-02). Los nombres coinciden con el enum {@code EstadoUnidad} de
 * {@code docs/contrato/openapi.yaml}; no se deben renombrar sin cambiar el contrato.
 */
public enum EstadoUnidad {

    DISPONIBLE,
    PRESTADA,
    EN_REVISION,
    FUERA_DE_SERVICIO;

    /** Solo una unidad disponible se puede prestar (RF-04, RF-10). */
    public boolean sePuedePrestar() {
        return this == DISPONIBLE;
    }

    /** Indica si el texto corresponde a un estado permitido (sin distinguir mayúsculas ni espacios laterales). */
    public static boolean esValido(String texto) {
        return buscar(texto).isPresent();
    }

    /**
     * Convierte un texto en estado.
     *
     * @throws ReglaNegocioException si el texto no es un estado permitido; el mensaje lista los válidos
     */
    public static EstadoUnidad desdeTexto(String texto) {
        return buscar(texto).orElseThrow(() -> new ReglaNegocioException(
                "Estado no permitido: " + texto + ". Valores válidos: " + permitidosComoTexto()));
    }

    /** Lista legible de los estados permitidos, para mensajes de error. */
    public static String permitidosComoTexto() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }

    private static Optional<EstadoUnidad> buscar(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        String normalizado = texto.strip();
        return Arrays.stream(values()).filter(e -> e.name().equalsIgnoreCase(normalizado)).findFirst();
    }
}
