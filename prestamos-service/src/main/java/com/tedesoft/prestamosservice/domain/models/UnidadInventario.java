package com.tedesoft.prestamosservice.domain.models;

/**
 * Lo que {@code prestamos-service} necesita saber de una unidad de inventario para decidir si un
 * préstamo procede: su estado y las reglas de su tipo.
 *
 * <p>{@code tipoId} se guarda en cada préstamo (referencia lógica, sin clave foránea entre bases) para
 * poder agrupar por tipo después (RF-09, RF-11).
 *
 * <p>Las reglas ({@code duracionMaximaDias}, {@code requiereAutorizacion}) llegan en la misma respuesta
 * que el estado (D-01): el dominio las aplica sin saber de antemano qué tipos de equipo existen (RQ-01).
 */
public record UnidadInventario(
        Long id,
        String numeroInventario,
        EstadoUnidad estado,
        Long tipoId,
        int duracionMaximaDias,
        boolean requiereAutorizacion
) {

    public boolean estaDisponible() {
        return estado == EstadoUnidad.DISPONIBLE;
    }
}
