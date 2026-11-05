package com.tedesoft.prestamosservice.delivery.rest.dto;

import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;

import java.time.LocalDate;

/** Representación de un préstamo hacia el exterior. */
public record PrestamoResponseDto(
        Long id,
        Long unidadId,
        Long tipoId,
        String solicitanteId,
        EstadoPrestamo estado,
        int duracionAplicadaDias,
        LocalDate fechaSolicitud,
        LocalDate fechaDevolucionPrevista
) {

    public static PrestamoResponseDto desde(Prestamo prestamo) {
        return new PrestamoResponseDto(
                prestamo.getId(),
                prestamo.getUnidadId(),
                prestamo.getTipoId(),
                prestamo.getSolicitanteId(),
                prestamo.getEstado(),
                prestamo.getDuracionAplicadaDias(),
                prestamo.getFechaSolicitud(),
                prestamo.getFechaPrevista());
    }
}
