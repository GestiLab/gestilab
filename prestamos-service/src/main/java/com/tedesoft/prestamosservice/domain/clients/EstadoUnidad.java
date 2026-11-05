package com.tedesoft.prestamosservice.domain.clients;

/** Estado de una unidad según el contrato de inventario (docs/contrato/openapi.yaml). */
public enum EstadoUnidad {
    DISPONIBLE,
    PRESTADA,
    EN_REVISION,
    FUERA_DE_SERVICIO
}
