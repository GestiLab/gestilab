package com.tedesoft.inventarioservice.delivery.rest.dto;

import com.tedesoft.inventarioservice.domain.entities.TipoEquipo;

/**
 * Representación de un tipo de equipo hacia el exterior. Los nombres de campo siguen el esquema
 * {@code TipoEquipo} de {@code docs/contrato/openapi.yaml}, más la descripción.
 */
public record TipoEquipoResponseDto(
        Long id,
        String nombre,
        String descripcion,
        int duracionMaximaDias,
        boolean requiereAutorizacion
) {

    public static TipoEquipoResponseDto desde(TipoEquipo tipo) {
        return new TipoEquipoResponseDto(
                tipo.getId(),
                tipo.getNombre(),
                tipo.getDescripcion(),
                tipo.getDuracionMaximaDias(),
                tipo.isRequiereAutorizacion());
    }
}
