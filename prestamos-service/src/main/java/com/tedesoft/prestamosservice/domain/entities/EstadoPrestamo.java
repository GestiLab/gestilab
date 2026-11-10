package com.tedesoft.prestamosservice.domain.entities;

import java.util.Arrays;
import java.util.List;

/**
 * Estado explícito de un préstamo.
 *
 * <p>El retraso NO es un estado: se deriva de la fecha prevista y del reloj inyectado (D-04).
 * La autorización previa (PENDIENTE_AUTORIZACION, P-01) aún no está decidida. Agregar un estado exige
 * una migración: con {@code ddl-auto=update} Hibernate no actualiza el CHECK que ya tiene la tabla
 * {@code prestamo} en una base existente, así que el nuevo valor sería rechazado hasta reemplazarlo.
 */
public enum EstadoPrestamo {

    /** Solicitado y aún no entregado. Ya ocupa cupo del tope por persona. */
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

    /** Estados que ocupan cupo; se derivan de {@link #estaActivo()} para no repetir la regla. */
    public static List<EstadoPrestamo> activos() {
        return Arrays.stream(values()).filter(EstadoPrestamo::estaActivo).toList();
    }
}
