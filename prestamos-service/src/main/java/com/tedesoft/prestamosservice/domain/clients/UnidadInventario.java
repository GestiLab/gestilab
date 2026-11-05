package com.tedesoft.prestamosservice.domain.clients;

/**
 * Lo que préstamos necesita saber de una unidad, en una sola respuesta de inventario: su estado y las
 * reglas de su tipo. Préstamos aplica estas reglas sin conocer de antemano qué tipos existen
 * (D-01, RQ-01 Modificabilidad).
 */
public record UnidadInventario(
        Long id,
        EstadoUnidad estado,
        Long tipoId,
        int duracionMaximaDias,
        boolean requiereAutorizacion
) {
}
