package com.tedesoft.prestamosservice.domain.services;

import com.tedesoft.prestamosservice.domain.clients.IInventarioClient;
import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.exception.DependenciaNoDisponibleException;
import com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.prestamosservice.domain.exception.ReglaNegocioException;
import com.tedesoft.prestamosservice.domain.models.EstadoUnidad;
import com.tedesoft.prestamosservice.domain.models.UnidadInventario;
import com.tedesoft.prestamosservice.domain.repositories.IPrestamoDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de préstamos. No contiene reglas por tipo de equipo: la duración llega en la respuesta de
 * inventario y se aplica tal cual, por eso un tipo nuevo no obliga a cambiar este servicio (RQ-01).
 */
@Service
@RequiredArgsConstructor
public class PrestamoServiceImpl implements IPrestamoService {

    private final IPrestamoDao prestamoDao;
    private final IInventarioClient inventarioClient;
    private final IFechaService fechaService;

    // Sin @Transactional a propósito: la llamada HTTP a inventario no debe mantener abierta una conexión
    // de base de datos (junto con spring.jpa.open-in-view=false). El save() corre en su propia transacción.
    @Override
    public Prestamo solicitar(Long unidadId, String solicitanteId) {
        UnidadInventario unidad = validarRespuesta(inventarioClient.obtenerUnidad(unidadId));

        if (unidad.estado() != EstadoUnidad.DISPONIBLE) {
            throw new ReglaNegocioException("La unidad " + unidadId + " no está disponible (estado: "
                    + unidad.estado().etiqueta() + ")");
        }
        // Inventario sigue diciendo DISPONIBLE hasta la entrega (RF-07): préstamos protege su propia unidad
        if (prestamoDao.existsByUnidadIdAndEstadoIn(unidadId, EstadoPrestamo.activos())) {
            throw new ReglaNegocioException("La unidad " + unidadId + " ya tiene un préstamo activo");
        }

        Prestamo prestamo = Prestamo.solicitar(unidadId, unidad.tipoId(), solicitanteId,
                unidad.duracionMaximaDias(), fechaService.hoy());
        return prestamoDao.save(prestamo);
    }

    @Override
    @Transactional(readOnly = true)
    public Prestamo obtenerPorId(Long id) {
        return prestamoDao.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Préstamo " + id + " no encontrado"));
    }

    /** Una respuesta que incumple el contrato es un fallo de la dependencia, no un error nuestro (503, no 500). */
    private UnidadInventario validarRespuesta(UnidadInventario unidad) {
        if (unidad == null || unidad.estado() == null || unidad.tipoId() == null
                || unidad.duracionMaximaDias() <= 0) {
            throw new DependenciaNoDisponibleException("inventario respondió con datos inválidos");
        }
        return unidad;
    }
}
