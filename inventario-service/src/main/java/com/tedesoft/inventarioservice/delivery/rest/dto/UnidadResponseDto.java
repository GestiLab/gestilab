package com.tedesoft.inventarioservice.delivery.rest.dto;

import com.tedesoft.inventarioservice.domain.entities.EstadoUnidad;
import com.tedesoft.inventarioservice.domain.entities.Unidad;

/**
 * Representación de una unidad hacia el exterior. Sigue el esquema {@code Unidad} de
 * {@code docs/contrato/openapi.yaml} (id, numeroInventario, estado, tipo con sus reglas), más
 * {@code observaciones}, que es un campo opcional adicional.
 */
public record UnidadResponseDto(
        Long id,
        String numeroInventario,
        EstadoUnidad estado,
        String observaciones,
        TipoEquipoResponseDto tipo
) {

    public static UnidadResponseDto desde(Unidad unidad) {
        return new UnidadResponseDto(
                unidad.getId(),
                unidad.getNumeroInventario(),
                unidad.getEstado(),
                unidad.getObservaciones(),
                TipoEquipoResponseDto.desde(unidad.getTipo()));
    }
}
