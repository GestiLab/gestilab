package com.tedesoft.inventario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Cuerpo de entrada para crear (POST) o reemplazar (PUT) un tipo de equipo. */
public record TipoEquipoRequestDto(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
        String nombre,

        @Size(max = 500, message = "La descripción no puede superar 500 caracteres")
        String descripcion,

        @NotNull(message = "La duración máxima es obligatoria")
        @Positive(message = "La duración máxima debe ser mayor que cero")
        Integer duracionMaximaDias,

        @NotNull(message = "Debe indicar si requiere autorización previa")
        Boolean requiereAutorizacionPrevia
) {
}
