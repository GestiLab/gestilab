package com.tedesoft.prestamosservice.domain.entities;

/**
 * Estado explícito de un préstamo.
 *
 * <p>El retraso NO es un estado: se deriva de la fecha prevista y del reloj inyectado (D-04).
 * La autorización previa (PENDIENTE_AUTORIZACION, P-01) aún no está decidida; cuando se decida se
 * agrega aquí sin tocar las demás reglas.
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
}
