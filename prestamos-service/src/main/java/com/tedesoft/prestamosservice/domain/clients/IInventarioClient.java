package com.tedesoft.prestamosservice.domain.clients;

import com.tedesoft.prestamosservice.domain.exception.DependenciaNoDisponibleException;
import com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException;

/**
 * Puerto hacia inventario-service. El dominio solo conoce esta interfaz: la implementación HTTP vive
 * fuera de {@code domain/}, y en las pruebas se sustituye por un simulado, sin levantar inventario.
 */
@FunctionalInterface
public interface IInventarioClient {

    /**
     * Consulta una unidad con su estado y las reglas de su tipo.
     *
     * @throws RecursoNoEncontradoException     si inventario no conoce la unidad (404).
     * @throws DependenciaNoDisponibleException si inventario no responde o agota el tiempo (503).
     */
    UnidadInventario obtenerUnidad(Long unidadId);
}
