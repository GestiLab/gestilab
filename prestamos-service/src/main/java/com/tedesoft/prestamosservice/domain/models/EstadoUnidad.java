package com.tedesoft.prestamosservice.domain.models;

/**
 * Estado de una unidad según {@code inventario-service}. Es la vista que tiene préstamos del enum
 * {@code EstadoUnidad} de {@code docs/contrato/openapi.yaml}; no comparte clase con el otro servicio.
 */
public enum EstadoUnidad {
    DISPONIBLE,
    PRESTADA,
    EN_REVISION,
    FUERA_DE_SERVICIO
}
