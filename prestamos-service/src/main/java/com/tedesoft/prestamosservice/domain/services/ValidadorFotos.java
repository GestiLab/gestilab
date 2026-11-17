
package com.tedesoft.prestamosservice.domain.services;

import com.tedesoft.prestamosservice.domain.exception.FotoInvalidaException;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

public final class ValidadorFotos {

    // Límite: 5 MB.
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private static final byte[] FIRMA_PNG = {
            (byte) 137, 80, 78, 71, 13, 10, 26, 10
    };

    private ValidadorFotos() {}

    public static String validar(byte[] contenido, String tipoMime) {

        if (contenido == null || contenido.length == 0) {
            throw new FotoInvalidaException(
                    "La fotografía es obligatoria"
            );
        }

        if (contenido.length > MAX_BYTES) {
            throw new FotoInvalidaException(
                    "La fotografía supera el tamaño máximo de 5 MB"
            );
        }

        String tipoReal;

        if (contenido.length >= 8 &&
                Arrays.equals(
                        Arrays.copyOf(contenido, 8),
                        FIRMA_PNG
                )) {
            tipoReal = "image/png";
        } else if (
                contenido.length >= 3 &&
                        (contenido[0] & 0xFF) == 0xFF &&
                        (contenido[1] & 0xFF) == 0xD8 &&
                        (contenido[2] & 0xFF) == 0xFF
        ) {
            tipoReal = "image/jpeg";
        } else {
            throw new FotoInvalidaException(
                    "El archivo no es una imagen PNG o JPEG válida"
            );
        }

        if (!tipoReal.equals(tipoMime)) {
            throw new FotoInvalidaException(
                    "El tipo declarado no coincide con la imagen"
            );
        }

        try {
            var imagen = ImageIO.read(
                    new ByteArrayInputStream(contenido)
            );

            if (imagen == null) {
                throw new FotoInvalidaException(
                        "El contenido no es una imagen válida"
                );
            }

        } catch (IOException ex) {
            throw new FotoInvalidaException(
                    "No se pudo interpretar la imagen"
            );
        }

        return tipoReal;
    }
}
