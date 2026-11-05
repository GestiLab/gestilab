package com.tedesoft.prestamosservice.domain.repositories;

import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface IPrestamoDao extends JpaRepository<Prestamo, Long> {

    /** ¿La unidad ya está en un préstamo que no se ha devuelto? */
    boolean existsByUnidadIdAndEstadoIn(Long unidadId, Collection<EstadoPrestamo> estados);

    long countBySolicitanteIdAndEstadoIn(String solicitanteId, Collection<EstadoPrestamo> estados);
}
