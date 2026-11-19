package com.tedesoft.inventarioservice.domain.entities;

/** Qué pasó al aplicar un evento de cambio de estado a una unidad (SCRUM-21). */
public enum ResultadoEventoEstado {

    /** El estado cambió. */
    APLICADO,
    /** La unidad ya estaba en ese estado (evento repetido): no hay cambio ni error. */
    SIN_CAMBIO,
    /** El evento es anterior (o simultáneo, con otro estado) al último aplicado: se conserva lo más reciente. */
    FUERA_DE_ORDEN,
    /** La unidad está en revisión o fuera de servicio: el evento no puede cambiarla. */
    NO_APLICABLE,
    /** El evento trae un estado que este flujo no maneja: solo PRESTADA y DISPONIBLE. */
    ESTADO_NO_ADMITIDO,
    /** La unidad no existe. */
    UNIDAD_INEXISTENTE
}
