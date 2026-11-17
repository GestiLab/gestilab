package com.tedesoft.prestamosservice.domain.services;

import com.tedesoft.prestamosservice.domain.clients.IAlmacenamientoFotos;
import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.models.PrestamoRetrasado;

import java.util.List;

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

    /**
     * Lista los préstamos entregados cuya fecha de devolución ya pasó, con los días de retraso respecto a
     * hoy según el reloj inyectado. El que vence hoy no cuenta. El más atrasado va primero.
     */
    List<PrestamoRetrasado> listarRetrasados();

    /**
     * Lista préstamos, el más reciente primero.
     *
     * @param persona quien solicitó el préstamo; {@code null} = cualquiera. Se recorta como al guardarla.
     * @param estado  estado del préstamo; {@code null} = cualquiera.
     * @return lista vacía si nada coincide, nunca un error.
     */
    List<Prestamo> listar(String persona, EstadoPrestamo estado);


    /**
     * Registra la entrega de una unidad.
     * Cambia el préstamo de SOLICITADO a ENTREGADO.
     */
    Prestamo entregar(Long prestamoId);

    /**
     * Registra la devolución de una unidad.
     * Cambia el préstamo de ENTREGADO a DEVUELTO.
     */
    Prestamo recibir(Long prestamoId);


    /** Entrega una unidad registrando su fotografía.*/
    Prestamo entregarConFoto(
            Long prestamoId,
            byte[] contenido,
            String tipoMime
    );

    /** Recibe una unidad registrando su fotografía.*/
    Prestamo recibirConFoto(
            Long prestamoId,
            byte[] contenido,
            String tipoMime
    );

    /**
     * Fotografía tomada al entregar la unidad (SCRUM-22, CA-3).
     *
     * @throws com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException si el préstamo no
     *         existe o aún no tiene esa fotografía
     */
    IAlmacenamientoFotos.Foto obtenerFotoEntrega(Long prestamoId);

    /** Fotografía tomada al recibir la unidad; existe solo en un préstamo devuelto (SCRUM-22, CA-3). */
    IAlmacenamientoFotos.Foto obtenerFotoDevolucion(Long prestamoId);

}
