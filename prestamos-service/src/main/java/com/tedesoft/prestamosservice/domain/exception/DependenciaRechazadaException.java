package com.tedesoft.prestamosservice.domain.exception;

/**
 * Un servicio del que depende {@code prestamos-service} respondió, pero rechazó la petición (4xx distinto
 * de 404: credenciales, permisos o contrato roto). No es una caída: es un problema de integración que
 * reintentar no arregla. El handler global la traduce a 502 (ver {@code docs/guia-errores.md}).
 */
public class DependenciaRechazadaException extends RuntimeException {
    public DependenciaRechazadaException(String mensaje) { super(mensaje); }
}
