package com.tedesoft.prestamosservice.domain.clients;

/** Estado de una unidad según el contrato de inventario (docs/contrato/openapi.yaml). */
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
