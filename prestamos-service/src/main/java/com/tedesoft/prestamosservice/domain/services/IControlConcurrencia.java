package com.tedesoft.prestamosservice.domain.services;

import com.tedesoft.prestamosservice.domain.entities.Prestamo;

import java.util.function.Supplier;

/**
 * Ejecuta una acción en una transacción y de a una por clave: dos solicitudes de la misma persona no pueden
 * comprobar el tope de 3 al mismo tiempo (comprobar y luego guardar deja pasar a las dos). El dominio no sabe
 * cómo se logra; la implementación decide (en PostgreSQL, un bloqueo asesor de transacción).
 */
@FunctionalInterface
public interface IControlConcurrencia {

    Prestamo conExclusividad(String clave, Supplier<Prestamo> accion);
}
