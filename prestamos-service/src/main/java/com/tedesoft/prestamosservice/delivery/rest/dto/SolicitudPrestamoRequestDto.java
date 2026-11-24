package com.tedesoft.prestamosservice.delivery.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Cuerpo de entrada para solicitar una unidad.
 *
 * <p>Quién solicita no viene en el cuerpo: sale del {@code sub} del token (SCRUM-24, CA-2). Si una petición trae
 * de todos modos un {@code solicitanteId}, se ignora: nunca se usa.
 */
public record SolicitudPrestamoRequestDto(
        @NotNull(message = "La unidad es obligatoria")
        @Positive(message = "El id de la unidad debe ser mayor que cero")
        Long unidadId
) {
}
