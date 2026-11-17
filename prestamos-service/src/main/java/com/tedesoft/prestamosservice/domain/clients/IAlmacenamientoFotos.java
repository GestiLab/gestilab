
package com.tedesoft.prestamosservice.domain.clients;

public interface IAlmacenamientoFotos {

    // Guarda la fotografía y devuelve una clave única.
    String guardar(byte[] contenido, String tipoMime);

    // Recupera una fotografía mediante su clave.
    Foto recuperar(String clave);

    void eliminar(String clave);

    // Representa la imagen recuperada.
    record Foto(byte[] contenido, String tipoMime) {
    }
}
