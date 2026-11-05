package com.tedesoft.prestamosservice.domain.services;

import com.tedesoft.prestamosservice.domain.clients.EstadoUnidad;
import com.tedesoft.prestamosservice.domain.clients.IInventarioClient;
import com.tedesoft.prestamosservice.domain.clients.UnidadInventario;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.prestamosservice.domain.exception.ReglaNegocioException;
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
    // de base de datos. El save() ya corre en su propia transacción.
    @Override
    public Prestamo solicitar(Long unidadId, String solicitanteId) {
        UnidadInventario unidad = inventarioClient.obtenerUnidad(unidadId);

        if (unidad.estado() != EstadoUnidad.DISPONIBLE) {
            throw new ReglaNegocioException("La unidad " + unidadId + " no está disponible (estado: "
                    + unidad.estado().name().toLowerCase().replace('_', ' ') + ")");
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
}
