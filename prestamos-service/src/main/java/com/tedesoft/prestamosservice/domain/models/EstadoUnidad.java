package com.tedesoft.prestamosservice.domain.models;

/**
 * Estado de una unidad según {@code inventario-service}. Es la vista que tiene préstamos del enum
 * {@code EstadoUnidad} de {@code docs/contrato/openapi.yaml}; no comparte clase con el otro servicio.
 */
public enum EstadoUnidad {
    DISPONIBLE("disponible"),
    PRESTADA("prestada"),
    EN_REVISION("en revisión"),
    FUERA_DE_SERVICIO("fuera de servicio");

    private final String etiqueta;

    EstadoUnidad(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Texto legible para mensajes dirigidos a la persona que consume la API. */
    public String etiqueta() {
        return etiqueta;
    }
}
