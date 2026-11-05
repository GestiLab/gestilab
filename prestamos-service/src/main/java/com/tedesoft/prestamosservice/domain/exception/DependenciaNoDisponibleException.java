package com.tedesoft.prestamosservice.domain.exception;

/** Otro servicio del que depende este no responde. Se traduce a 503 (docs/guia-errores.md). */
public class DependenciaNoDisponibleException extends RuntimeException {
    public DependenciaNoDisponibleException(String mensaje) { super(mensaje); }
    public DependenciaNoDisponibleException(String mensaje, Throwable causa) { super(mensaje, causa); }
}
