package com.tedesoft.prestamosservice.domain.repositories;

import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface IPrestamoDao extends JpaRepository<Prestamo, Long> {

    /** ¿La unidad ya está en un préstamo que no se ha devuelto? */
    boolean existsByUnidadIdAndEstadoIn(Long unidadId, Collection<EstadoPrestamo> estados);

    long countBySolicitanteIdAndEstadoIn(String solicitanteId, Collection<EstadoPrestamo> estados);

    List<Prestamo> findByEstado(EstadoPrestamo estado);

    // Listados (SCRUM-24): el más reciente primero, con el id como criterio estable
    List<Prestamo> findAllByOrderByIdDesc();

    List<Prestamo> findBySolicitanteIdOrderByIdDesc(String solicitanteId);

    List<Prestamo> findByEstadoOrderByIdDesc(EstadoPrestamo estado);

    List<Prestamo> findBySolicitanteIdAndEstadoOrderByIdDesc(String solicitanteId, EstadoPrestamo estado);
}
