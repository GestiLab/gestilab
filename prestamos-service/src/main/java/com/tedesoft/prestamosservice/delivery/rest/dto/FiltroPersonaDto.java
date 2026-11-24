package com.tedesoft.prestamosservice.delivery.rest.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Filtro opcional por persona del listado de préstamos: primero se recorta y después se valida (entre 1 y 100
 * caracteres, sin caracteres de control); {@code null} significa que no se filtra. Qué persona puede pedir cada
 * rol lo decide el controlador (SCRUM-24).
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
