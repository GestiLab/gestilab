package com.tedesoft.prestamosservice.domain.clients;

import com.tedesoft.prestamosservice.domain.exception.DependenciaNoDisponibleException;
import com.tedesoft.prestamosservice.domain.exception.DependenciaRechazadaException;
import com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.prestamosservice.domain.models.UnidadInventario;

/**
 * Puerto de salida hacia {@code inventario-service}. El dominio solo conoce esta interfaz: el HTTP, la
 * URL y los tiempos de espera viven en la implementación (RQ-02: la lógica se prueba sin levantar nada).
 */
public interface IInventarioClient {

    /**
     * Consulta una unidad y las reglas de su tipo en una sola llamada.
     *
     * @throws RecursoNoEncontradoException   si inventario responde que la unidad no existe.
     * @throws DependenciaNoDisponibleException si inventario no responde, tarda demasiado, falla (5xx) o
     *                                          contesta algo que no cumple el contrato.
     * @throws DependenciaRechazadaException    si inventario responde, pero rechaza la petición (4xx
     *                                          distinto de 404: credenciales, permisos o contrato).
     */
    UnidadInventario obtenerUnidad(Long id);
}
