
package com.tedesoft.prestamosservice.integration;

import com.tedesoft.prestamosservice.domain.clients.IAlmacenamientoFotos;
import com.tedesoft.prestamosservice.domain.clients.IEventoUnidadPendiente;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.repositories.IPrestamoDao;
import com.tedesoft.prestamosservice.domain.services.IPrestamoService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "gestilab.eventos.procesador.habilitado=false",
        "management.health.rabbit.enabled=false",
        "gestilab.fotos.s3.habilitado=false"
})
@Testcontainers(disabledWithoutDocker = true)
class PrestamoFotosRollbackIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private IPrestamoService servicio;

    @Autowired
    private IPrestamoDao prestamoDao;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private IAlmacenamientoFotos almacenamientoFotos;

    @MockitoBean
    private IEventoUnidadPendiente eventosPendientes;

    @BeforeEach
    void prepararBaseDeDatos() {
        jdbc.update("DELETE FROM evento_unidad_pendiente");
        jdbc.update("DELETE FROM prestamo");
    }

    private byte[] crearImagen() throws IOException {

        BufferedImage imagen = new BufferedImage(
                2, 2, BufferedImage.TYPE_INT_RGB
        );

        ByteArrayOutputStream salida =
                new ByteArrayOutputStream();

        if (!ImageIO.write(imagen, "png", salida)) {
            throw new IOException(
                    "No se pudo generar la imagen"
            );
        }

        return salida.toByteArray();
    }

    @Test
    void errorDespuesDeSubirFoto_debeRevertirEntrega()
            throws Exception {

        // 1. Crear un préstamo real en PostgreSQL.
        Prestamo prestamo = prestamoDao.save(
                Prestamo.solicitar(
                        14L,
                        2L,
                        "ana",
                        7,
                        LocalDate.of(2026, 11, 13)
                )
        );

        Long prestamoId = prestamo.getId();

        // 2. Simular una fotografía guardada en S3.
        String clave = "prestamos/fotos/rollback-test";

        when(almacenamientoFotos.guardar(
                any(byte[].class),
                eq("image/png")
        )).thenReturn(clave);

        // 3. Simular un fallo después de guardar la foto.
        doThrow(new IllegalStateException(
                "Fallo simulado registrando el evento"
        )).when(eventosPendientes).guardar(
                eq(14L),
                eq("PRESTADA"),
                any(OffsetDateTime.class)
        );

        // 4. La operación debe fallar.
        assertThatThrownBy(
                () -> servicio.entregarConFoto(
                        prestamoId,
                        crearImagen(),
                        "image/png"
                )
        ).isInstanceOf(IllegalStateException.class);

        // 5. Verificar que se intentó eliminar la foto.
        verify(almacenamientoFotos).eliminar(clave);

        // 6. Comprobar el estado REAL de PostgreSQL.
        String estado = jdbc.queryForObject(
                "SELECT estado FROM prestamo WHERE id = ?",
                String.class,
                prestamoId
        );

        assertThat(estado).isEqualTo("SOLICITADO");

        // 7. La clave de la fotografía no debe persistir.
        String fotoGuardada = jdbc.queryForObject(
                "SELECT foto_entrega_clave FROM prestamo WHERE id = ?",
                String.class,
                prestamoId
        );

        assertThat(fotoGuardada).isNull();
    }
}
