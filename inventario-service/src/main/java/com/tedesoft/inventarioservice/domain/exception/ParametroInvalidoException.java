package com.tedesoft.inventarioservice.domain.exception;

/**
 * Un parámetro de consulta no cumple las reglas (página negativa, tamaño menor o igual que cero, estado
 * desconocido...). El manejador global lo traduce a 400 con el campo en {@code errores}.
 */
public class ParametroInvalidoException extends RuntimeException {

    private final String campo;

    public ParametroInvalidoException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
