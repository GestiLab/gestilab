package com.tedesoft.prestamosservice.domain.exception;

/**
 * Un servicio del que depende {@code prestamos-service} no respondió o respondió con error.
 * El handler global la traduce a 503 (ver {@code docs/guia-errores.md}). La causa original queda solo
 * para el log: nunca viaja al cliente.
 */
public class DependenciaNoDisponibleException extends RuntimeException {
    public DependenciaNoDisponibleException(String mensaje, Throwable causa) { super(mensaje, causa); }
    public DependenciaNoDisponibleException(String mensaje) { super(mensaje); }
}
