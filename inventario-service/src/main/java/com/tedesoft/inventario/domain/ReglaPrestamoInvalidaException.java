package com.tedesoft.inventario.domain;

import lombok.Getter;

/**
 * Se lanza cuando se intenta crear o modificar un tipo de equipo con reglas que violan el dominio.
 * Indica el campo afectado para que el manejador global pueda reportarlo.
 */
@Getter
public class ReglaPrestamoInvalidaException extends RuntimeException {

    private final String campo;

    public ReglaPrestamoInvalidaException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }
}
