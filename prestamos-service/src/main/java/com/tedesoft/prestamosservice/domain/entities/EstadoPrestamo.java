
package com.tedesoft.prestamosservice.domain.entities;

import java.util.Arrays;
import java.util.List;

/**
 * Estado explícito de un préstamo.
 *
 * El retraso NO es un estado: se deriva de la fecha prevista
 * y del reloj inyectado (D-04).
 *
 * Los préstamos pendientes de autorización también cuentan
 * como activos y reservan la unidad.
 *
 * En PostgreSQL se debe actualizar la restricción CHECK
 * de la columna estado para admitir el nuevo valor.
 */
public enum EstadoPrestamo {

    /** Espera la autorización del auxiliar. */
    PENDIENTE_AUTORIZACION(true),

    /** Solicitado y aún no entregado. */
    SOLICITADO(true),

    /** La unidad está en manos de la persona. */
    ENTREGADO(true),

    /** Devuelto: el préstamo se cerró y libera su cupo. */
    DEVUELTO(false);

    private final boolean activo;

    EstadoPrestamo(boolean activo) {
        this.activo = activo;
    }

    /** Un préstamo activo es el que aún no se ha devuelto. */
    public boolean estaActivo() {
        return activo;
    }

    /** Estados que ocupan cupo y reservan una unidad. */
    public static List<EstadoPrestamo> activos() {
        return Arrays.stream(values())
                .filter(EstadoPrestamo::estaActivo)
                .toList();
    }
}
