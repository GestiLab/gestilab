package com.tedesoft.inventarioservice.delivery.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de entrada para crear (POST) o reemplazar (PUT) una unidad. El estado llega como texto para
 * poder responder 400 con la lista de estados permitidos (ver {@link EstadoUnidadValido}).
 */
public record UnidadRequestDto(

        @NotBlank(message = "El número de inventario es obligatorio")
        @Size(max = 50, message = "El número de inventario no puede superar 50 caracteres")
        String numeroInventario,

        @NotNull(message = "El estado es obligatorio")
        @EstadoUnidadValido
        String estado,

        @Size(max = 500, message = "Las observaciones no pueden superar 500 caracteres")
        String observaciones,

        @NotNull(message = "El tipo de equipo es obligatorio")
        @Positive(message = "El id del tipo de equipo debe ser mayor que cero")
        Long tipoEquipoId
) {
}
