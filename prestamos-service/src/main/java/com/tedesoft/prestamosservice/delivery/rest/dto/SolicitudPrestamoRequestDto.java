package com.tedesoft.prestamosservice.delivery.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de entrada para solicitar una unidad.
 *
 * <p>{@code solicitanteId} es provisional: cuando llegue la seguridad (SCRUM-20) la persona saldrá del
 * {@code sub} del token y este campo desaparecerá.
 */
public record SolicitudPrestamoRequestDto(

        @NotNull(message = "La unidad es obligatoria")
        @Positive(message = "El id de la unidad debe ser mayor que cero")
        Long unidadId,

        @NotBlank(message = "El solicitante es obligatorio")
        @Size(max = 100, message = "El solicitante no puede superar 100 caracteres")
        @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "El solicitante no puede contener caracteres de control")
        String solicitanteId
) {

    /**
     * Recorta el solicitante con el mismo criterio que usa el dominio ({@code strip}, que entiende los
     * espacios Unicode). Así la validación y el dominio coinciden sobre qué es "en blanco" y sobre el
     * largo real que se guarda.
     */
    public SolicitudPrestamoRequestDto {
        solicitanteId = solicitanteId == null ? null : solicitanteId.strip();
    }
}
