package com.tedesoft.prestamosservice.domain.services;

import com.tedesoft.prestamosservice.domain.entities.Prestamo;

public interface IPrestamoService {

    /**
     * Solicita una unidad: consulta a inventario su estado y las reglas de su tipo, y si está disponible
     * crea el préstamo con fecha prevista = hoy + duración máxima del tipo.
     *
     * @throws com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException
     *         si la unidad no existe.
     * @throws com.tedesoft.prestamosservice.domain.exception.ReglaNegocioException
     *         si la unidad no está disponible.
     * @throws com.tedesoft.prestamosservice.domain.exception.DependenciaNoDisponibleException
     *         si inventario no responde.
     */
    Prestamo solicitar(Long unidadId, String solicitanteId);

    /**
     * Consulta un préstamo por su id.
     *
     * @throws com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException
     *         si no existe.
     */
    Prestamo obtenerPorId(Long id);
}
