
package com.tedesoft.prestamosservice.domain.services;

import com.tedesoft.prestamosservice.domain.clients.IInventarioClient;
import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.exception.DependenciaNoDisponibleException;
import com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.prestamosservice.domain.exception.ReglaNegocioException;
import com.tedesoft.prestamosservice.domain.models.EstadoUnidad;
import com.tedesoft.prestamosservice.domain.models.PrestamoRetrasado;
import com.tedesoft.prestamosservice.domain.models.UnidadInventario;
import org.springframework.dao.DataIntegrityViolationException;
import com.tedesoft.prestamosservice.domain.repositories.IPrestamoDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.tedesoft.prestamosservice.domain.clients.IAlmacenamientoFotos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import com.tedesoft.prestamosservice.domain.clients.IEventoUnidadPendiente;

/**
 * Casos de uso de préstamos.
 *
 * La duración se obtiene del servicio de inventario.
 * SCRUM-19 agrega entrega y devolución de unidades.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrestamoServiceImpl implements IPrestamoService {

    /** Límite de unidades prestadas simultáneamente. */
    static final int TOPE_UNIDADES_SIMULTANEAS = 3;

    /** Ordena primero los préstamos con mayor retraso. */
    private static final Comparator<PrestamoRetrasado>
            MAS_ATRASADO_PRIMERO =
            Comparator.comparingLong(
                            PrestamoRetrasado::diasRetraso
                    ).reversed()
                    .thenComparing(PrestamoRetrasado::prestamoId);

    private final IPrestamoDao prestamoDao;
    private final IInventarioClient inventarioClient;
    private final IFechaService fechaService;
    private final IEventoUnidadPendiente eventosPendientes;
    private final IControlConcurrencia controlConcurrencia;

    private IAlmacenamientoFotos almacenamientoFotos;

    @Autowired
    public void configurarAlmacenamientoFotos(
            IAlmacenamientoFotos almacenamientoFotos) {
        this.almacenamientoFotos = almacenamientoFotos;
    }

    // ==========================================
    // SOLICITAR UN PRESTAMO (CODIGO EXISTENTE)
    // ==========================================

    @Override
    public Prestamo solicitar(Long unidadId, String solicitanteId) {

        String solicitante = solicitanteId.strip();

        validarTope(solicitante);

        UnidadInventario unidad = validarRespuesta(
                inventarioClient.obtenerUnidad(unidadId)
        );

        if (unidad.estado() != EstadoUnidad.DISPONIBLE) {
            throw new ReglaNegocioException(
                    "La unidad " + unidadId
                            + " no está disponible (estado: "
                            + unidad.estado().etiqueta() + ")"
            );
        }

        if (prestamoDao.existsByUnidadIdAndEstadoIn(
                unidadId, EstadoPrestamo.activos())) {
            throw unidadYaPrestada(unidadId);
        }

        int duracion = unidad.duracionMaximaDias();
        Long tipoId = unidad.tipoId();

        // Comprobar y luego guardar deja pasar a dos solicitudes simultáneas: se repite dentro del bloqueo
        // de la persona (tope de 3) y la base garantiza una sola por unidad (índice único parcial).
        return controlConcurrencia.conExclusividad(solicitante, () -> {
            validarTope(solicitante);

            if (prestamoDao.existsByUnidadIdAndEstadoIn(
                    unidadId, EstadoPrestamo.activos())) {
                throw unidadYaPrestada(unidadId);
            }

            try {
                return prestamoDao.saveAndFlush(Prestamo.solicitar(
                        unidadId, tipoId, solicitante, duracion, fechaService.hoy()));
            } catch (DataIntegrityViolationException e) {
                throw unidadYaPrestada(unidadId);
            }
        });
    }

    // ==========================================
    // SCRUM-19: ENTREGAR UNA UNIDAD
    // ==========================================


    @Override
    @Transactional
    public Prestamo entregar(Long prestamoId) {

        Prestamo prestamo = prestamoDao.buscarParaActualizar(prestamoId)
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                "Préstamo " + prestamoId + " no encontrado"
                        )
                );

        // SOLICITADO -> ENTREGADO
        prestamo.entregar();

        Prestamo guardado = prestamoDao.save(prestamo);

        // Registrar evento pendiente para inventario.
        eventosPendientes.guardar(
                guardado.getUnidadId(),
                EstadoUnidad.PRESTADA.name(),
                fechaService.ahora()
        );

        return guardado;
    }

    // ==========================================
    // SCRUM-19: RECIBIR UNA UNIDAD
    // ==========================================


    @Override
    @Transactional
    public Prestamo recibir(Long prestamoId) {

        Prestamo prestamo = prestamoDao.buscarParaActualizar(prestamoId)
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                "Préstamo " + prestamoId + " no encontrado"
                        )
                );

        // ENTREGADO -> DEVUELTO
        prestamo.recibir();

        Prestamo guardado = prestamoDao.save(prestamo);

        // Registrar evento pendiente para inventario.
        eventosPendientes.guardar(
                guardado.getUnidadId(),
                EstadoUnidad.DISPONIBLE.name(),
                fechaService.ahora()
        );

        return guardado;
    }


    @Override
    @Transactional
    public Prestamo entregarConFoto(
            Long prestamoId,
            byte[] contenido,
            String tipoMime) {

        String mimeValidado = ValidadorFotos.validar(
                contenido, tipoMime
        );

        Prestamo prestamo = prestamoDao.buscarParaActualizar(prestamoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Préstamo " + prestamoId + " no encontrado"
                ));

        // Comprueba que el préstamo se puede entregar.
        prestamo.entregar();

        // Subir la fotografía a S3.
        String clave = almacenamientoFotos.guardar(
                contenido, mimeValidado
        );

        registrarLimpiezaSiRollback(clave);

        // Guardar únicamente la clave en PostgreSQL.
        prestamo.registrarFotoEntrega(clave);

        Prestamo guardado = prestamoDao.save(prestamo);

        // Mantener el evento pendiente para inventario.
        eventosPendientes.guardar(
                guardado.getUnidadId(),
                EstadoUnidad.PRESTADA.name(),
                fechaService.ahora()
        );

        return guardado;
    }

    @Override
    @Transactional
    public Prestamo recibirConFoto(
            Long prestamoId,
            byte[] contenido,
            String tipoMime) {

        String mimeValidado = ValidadorFotos.validar(
                contenido, tipoMime
        );

        Prestamo prestamo = prestamoDao.buscarParaActualizar(prestamoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Préstamo " + prestamoId + " no encontrado"
                ));

        // Comprueba que el préstamo se puede devolver.
        prestamo.recibir();

        // Subir la fotografía a S3.
        String clave = almacenamientoFotos.guardar(
                contenido, mimeValidado
        );

        registrarLimpiezaSiRollback(clave);

        prestamo.registrarFotoDevolucion(clave);

        Prestamo guardado = prestamoDao.save(prestamo);

        // Mantener el evento pendiente para inventario.
        eventosPendientes.guardar(
                guardado.getUnidadId(),
                EstadoUnidad.DISPONIBLE.name(),
                fechaService.ahora()
        );

        return guardado;
    }

    // ==========================================
    // CONSULTAR PRESTAMO
    // ==========================================

    @Override
    @Transactional(readOnly = true)
    public Prestamo obtenerPorId(Long id) {
        return prestamoDao.findById(id)
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                "Préstamo " + id + " no encontrado"
                        )
                );
    }

    // ==========================================
    // LISTAR PRESTAMOS RETRASADOS
    // ==========================================

    @Override
    @Transactional(readOnly = true)
    public List<PrestamoRetrasado> listarRetrasados() {

        LocalDate hoy = fechaService.hoy();

        return prestamoDao.findByEstado(
                        EstadoPrestamo.ENTREGADO
                ).stream()
                .filter(prestamo ->
                        PrestamoRetrasado.aplicaA(prestamo, hoy))
                .map(prestamo ->
                        PrestamoRetrasado.desde(prestamo, hoy))
                .sorted(MAS_ATRASADO_PRIMERO)
                .toList();
    }

    // ==========================================
    // LISTAR PRESTAMOS
    // ==========================================

    @Override
    @Transactional(readOnly = true)
    public List<Prestamo> listar(
            String persona, EstadoPrestamo estado) {

        String solicitante =
                persona == null ? null : persona.strip();

        if (solicitante == null && estado == null) {
            return prestamoDao.findAllByOrderByIdDesc();
        }

        if (estado == null) {
            return prestamoDao
                    .findBySolicitanteIdOrderByIdDesc(solicitante);
        }

        if (solicitante == null) {
            return prestamoDao
                    .findByEstadoOrderByIdDesc(estado);
        }

        return prestamoDao
                .findBySolicitanteIdAndEstadoOrderByIdDesc(
                        solicitante, estado
                );
    }

    private static ReglaNegocioException unidadYaPrestada(Long unidadId) {
        return new ReglaNegocioException("La unidad " + unidadId + " ya tiene un préstamo activo");
    }

    // ==========================================
    // VALIDAR TOPE DE PRESTAMOS
    // ==========================================

    private void validarTope(String solicitante) {

        long activos =
                prestamoDao.countBySolicitanteIdAndEstadoIn(
                        solicitante, EstadoPrestamo.activos()
                );

        if (activos >= TOPE_UNIDADES_SIMULTANEAS) {
            throw new ReglaNegocioException(
                    "Se alcanzó el tope de "
                            + TOPE_UNIDADES_SIMULTANEAS
                            + " unidades prestadas a la vez. "
                            + "Devuelve una para solicitar otra."
            );
        }
    }

    // ==========================================
    // VALIDAR RESPUESTA DE INVENTARIO
    // ==========================================

    private UnidadInventario validarRespuesta(
            UnidadInventario unidad) {

        if (unidad == null
                || unidad.estado() == null
                || unidad.tipoId() == null
                || unidad.duracionMaximaDias() <= 0) {

            throw new DependenciaNoDisponibleException(
                    "inventario respondió con datos inválidos"
            );
        }

        return unidad;
    }


    private void registrarLimpiezaSiRollback(String clave) {

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCompletion(int status) {

                        if (status != STATUS_COMMITTED) {
                            try {
                                almacenamientoFotos.eliminar(clave);
                            } catch (RuntimeException ex) {
                                log.error(
                                        "No se pudo limpiar la fotografía de S3",
                                        ex
                                );
                            }
                        }
                    }
                }
        );
    }

}
