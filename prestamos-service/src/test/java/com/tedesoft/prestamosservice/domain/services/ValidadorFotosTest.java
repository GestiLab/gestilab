
package com.tedesoft.prestamosservice.domain.services;

import com.tedesoft.prestamosservice.domain.exception.FotoInvalidaException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ValidadorFotosTest {

    @Test
    void fotoObligatoria() {
        assertThrows(FotoInvalidaException.class,
                () -> ValidadorFotos.validar(null, null));
    }

    @Test
    void archivoDemasiadoGrande() {
        byte[] archivo = new byte[ValidadorFotos.MAX_BYTES + 1];

        assertThrows(FotoInvalidaException.class,
                () -> ValidadorFotos.validar(archivo, "image/png"));
    }

    @Test
    void archivoFalso() {
        byte[] archivo = "Esto no es una imagen".getBytes();

        assertThrows(FotoInvalidaException.class,
                () -> ValidadorFotos.validar(archivo, "image/jpeg"));
    }

    @Test
    void imagenPngValida() throws Exception {
        BufferedImage imagen = new BufferedImage(
                2, 2, BufferedImage.TYPE_INT_RGB
        );

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", salida);

        String tipo = ValidadorFotos.validar(
                salida.toByteArray(), "image/png"
        );

        assertEquals("image/png", tipo);
    }
}
