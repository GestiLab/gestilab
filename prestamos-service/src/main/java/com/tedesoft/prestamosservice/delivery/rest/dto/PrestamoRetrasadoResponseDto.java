package com.tedesoft.prestamosservice.delivery.rest.dto;

import com.tedesoft.prestamosservice.domain.models.PrestamoRetrasado;

import java.time.LocalDate;

/** Un renglón del listado de retrasos hacia el exterior. */
public record PrestamoRetrasadoResponseDto(
        Long prestamoId,
        Long unidadId,
        String solicitanteId,
        LocalDate fechaDevolucionPrevista,
        long diasRetraso
) {

    public static PrestamoRetrasadoResponseDto desde(PrestamoRetrasado retraso) {
        return new PrestamoRetrasadoResponseDto(
                retraso.prestamoId(),
                retraso.unidadId(),
                retraso.solicitanteId(),
                retraso.fechaDevolucionPrevista(),
                retraso.diasRetraso());
    }
}
