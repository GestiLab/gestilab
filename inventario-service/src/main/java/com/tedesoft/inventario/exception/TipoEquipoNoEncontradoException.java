package com.tedesoft.inventario.exception;

/** El tipo de equipo solicitado no existe (se traduce a HTTP 404). */
public class TipoEquipoNoEncontradoException extends RuntimeException {

    public TipoEquipoNoEncontradoException(Long id) {
        super("No existe un tipo de equipo con id " + id);
    }
}
