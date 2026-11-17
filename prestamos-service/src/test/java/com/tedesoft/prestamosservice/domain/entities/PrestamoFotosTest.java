
package com.tedesoft.prestamosservice.domain.entities;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PrestamoFotosTest {

    private Prestamo crearPrestamo() {
        return Prestamo.solicitar(
                14L,
                2L,
                "usuario-prueba",
                7,
                LocalDate.of(2026, 11, 16)
        );
    }

    @Test
    void debeRegistrarFotoAlEntregar() {
        Prestamo prestamo = crearPrestamo();

        prestamo.entregar();
        prestamo.registrarFotoEntrega("prestamos/fotos/foto-1");

        assertEquals(
                "prestamos/fotos/foto-1",
                prestamo.getFotoEntregaClave()
        );
    }

    @Test
    void debeRegistrarFotoAlDevolver() {
        Prestamo prestamo = crearPrestamo();

        prestamo.entregar();
        prestamo.recibir();
        prestamo.registrarFotoDevolucion("prestamos/fotos/foto-2");

        assertEquals(
                "prestamos/fotos/foto-2",
                prestamo.getFotoDevolucionClave()
        );
    }

    @Test
    void noDebeRegistrarFotoEntregaSiEstaSolicitado() {
        Prestamo prestamo = crearPrestamo();

        assertThrows(
                IllegalStateException.class,
                () -> prestamo.registrarFotoEntrega("foto-1")
        );
    }

    @Test
    void noDebeAceptarClaveVacia() {
        Prestamo prestamo = crearPrestamo();

        prestamo.entregar();

        assertThrows(
                IllegalArgumentException.class,
                () -> prestamo.registrarFotoEntrega(" ")
        );
    }

    @Test
    void noDebeSobrescribirFotoEntrega() {
        Prestamo prestamo = crearPrestamo();

        prestamo.entregar();
        prestamo.registrarFotoEntrega("foto-original");

        assertThrows(
                IllegalStateException.class,
                () -> prestamo.registrarFotoEntrega("foto-nueva")
        );

        assertEquals(
                "foto-original",
                prestamo.getFotoEntregaClave()
        );
    }
}
