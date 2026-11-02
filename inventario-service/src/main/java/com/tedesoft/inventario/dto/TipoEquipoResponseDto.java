package com.tedesoft.inventario.dto;

import com.tedesoft.inventario.domain.TipoEquipo;

/** Representación de un tipo de equipo hacia el exterior (incluye sus reglas de préstamo). */
public record TipoEquipoResponseDto(
        Long id,
        String nombre,
        String descripcion,
        int duracionMaximaDias,
        boolean requiereAutorizacionPrevia
) {

    public static TipoEquipoResponseDto desde(TipoEquipo tipo) {
        return new TipoEquipoResponseDto(
                tipo.getId(),
                tipo.getNombre(),
                tipo.getDescripcion(),
                tipo.getDuracionMaximaDias(),
                tipo.isRequiereAutorizacionPrevia());
    }
}
