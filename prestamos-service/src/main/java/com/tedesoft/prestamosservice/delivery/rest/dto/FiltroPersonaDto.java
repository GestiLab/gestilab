package com.tedesoft.prestamosservice.delivery.rest.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Filtro opcional por persona del listado de préstamos. Sigue las reglas de {@code solicitanteId} al
 * solicitar: primero se recorta y después se valida, y {@code null} significa que no se filtra.
 */
public record FiltroPersonaDto(

        @Size(min = 1, max = 100, message = "La persona debe tener entre 1 y 100 caracteres")
        @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "La persona no puede contener caracteres de control")
        String persona
) {

    public FiltroPersonaDto {
        persona = persona == null ? null : persona.strip();
    }
}
