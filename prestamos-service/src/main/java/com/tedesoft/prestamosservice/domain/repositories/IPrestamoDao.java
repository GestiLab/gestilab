package com.tedesoft.prestamosservice.domain.repositories;

import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IPrestamoDao extends JpaRepository<Prestamo, Long> {

    /**
     * Lee el préstamo bloqueándolo hasta que termine la transacción. Entregar o recibir dos veces a la vez
     * leería "SOLICITADO" en ambas y publicaría dos eventos: quien llega segundo espera y ve el estado nuevo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Prestamo p where p.id = :id")
    Optional<Prestamo> buscarParaActualizar(@Param("id") Long id);

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
