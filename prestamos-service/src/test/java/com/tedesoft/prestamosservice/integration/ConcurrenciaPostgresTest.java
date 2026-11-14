package com.tedesoft.prestamosservice.integration;

import com.tedesoft.prestamosservice.domain.clients.IInventarioClient;
import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.exception.ReglaNegocioException;
import com.tedesoft.prestamosservice.domain.models.EstadoUnidad;
import com.tedesoft.prestamosservice.domain.models.UnidadInventario;
import com.tedesoft.prestamosservice.domain.repositories.IPrestamoDao;
import com.tedesoft.prestamosservice.domain.services.IPrestamoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * SCRUM-19, CA-9, CA-10 y CA-13: la concurrencia se prueba contra PostgreSQL real, no contra H2. H2 no
 * reproduce el índice único parcial ni el bloqueo asesor que protegen estas reglas. Si Docker no está
 * disponible, las pruebas se omiten.
 */
@SpringBootTest(properties = {
        "gestilab.eventos.procesador.habilitado=false",
        "management.health.rabbit.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("SCRUM-19: concurrencia contra PostgreSQL real")
class ConcurrenciaPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean IInventarioClient inventario;
    @Autowired IPrestamoService servicio;
    @Autowired IPrestamoDao dao;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM evento_unidad_pendiente");
        jdbc.update("DELETE FROM prestamo");
        when(inventario.obtenerUnidad(anyLong())).thenAnswer(inv -> {
            long id = inv.getArgument(0);
            return new UnidadInventario(id, "U-" + id, EstadoUnidad.DISPONIBLE, 2L, 7, false);
        });
    }

    /** Lanza todas las tareas a la vez y devuelve el resultado de cada una (la excepción, si falló). */
    private static List<Object> simultaneas(List<Callable<Object>> tareas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tareas.size());
        CountDownLatch listos = new CountDownLatch(tareas.size());
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Object>> futuros = new ArrayList<>();
        for (Callable<Object> tarea : tareas) {
            futuros.add(pool.submit(() -> {
                listos.countDown();
                salida.await();
                try {
                    return tarea.call();
                } catch (Exception e) {
                    return e;
                }
            }));
        }
        listos.await();
        salida.countDown();
        List<Object> resultados = new ArrayList<>();
        for (Future<Object> f : futuros) {
            resultados.add(f.get());
        }
        pool.shutdown();
        return resultados;
    }

    private static long creados(List<Object> resultados) {
        return resultados.stream().filter(r -> r instanceof Prestamo).count();
    }

    private static List<ReglaNegocioException> rechazos(List<Object> resultados) {
        return resultados.stream().filter(r -> r instanceof ReglaNegocioException)
                .map(r -> (ReglaNegocioException) r).toList();
    }

    private long abiertosDeLaUnidad(long unidadId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM prestamo WHERE unidad_id = ? AND estado IN ('SOLICITADO','ENTREGADO')",
                Long.class, unidadId);
    }

    @Test
    @DisplayName("CA-9: ocho personas piden la misma unidad a la vez → un solo préstamo y siete 409")
    void unaUnidadUnSoloPrestamo() throws Exception {
        List<Callable<Object>> tareas = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            String persona = "persona" + i;
            tareas.add(() -> servicio.solicitar(14L, persona));
        }

        List<Object> resultados = simultaneas(tareas);

        assertThat(creados(resultados)).isEqualTo(1);
        assertThat(rechazos(resultados)).hasSize(7)
                .allSatisfy(e -> assertThat(e.getMessage()).contains("ya tiene un préstamo activo"));
        assertThat(abiertosDeLaUnidad(14L)).isEqualTo(1);
    }

    @Test
    @DisplayName("CA-10: quien tiene 2 préstamos y hace 5 solicitudes a la vez queda con exactamente 3")
    void topeDeTresBajoConcurrencia() throws Exception {
        servicio.solicitar(101L, "ana");
        servicio.solicitar(102L, "ana");

        List<Callable<Object>> tareas = new ArrayList<>();
        for (long unidad = 201; unidad <= 205; unidad++) {
            long u = unidad;
            tareas.add(() -> servicio.solicitar(u, "ana"));
        }

        List<Object> resultados = simultaneas(tareas);

        assertThat(creados(resultados)).isEqualTo(1);
        assertThat(rechazos(resultados)).hasSize(4)
                .allSatisfy(e -> assertThat(e.getMessage()).contains("tope de 3"));
        assertThat(dao.countBySolicitanteIdAndEstadoIn("ana", EstadoPrestamo.activos())).isEqualTo(3);
    }

    @Test
    @DisplayName("Entregar dos veces a la vez: una entrega y un solo evento")
    void entregarDosVecesGeneraUnSoloEvento() throws Exception {
        Prestamo p = dao.save(Prestamo.solicitar(14L, 2L, "ana", 7, LocalDate.of(2026, 11, 14)));

        List<Object> resultados = simultaneas(List.of(
                () -> servicio.entregar(p.getId()),
                () -> servicio.entregar(p.getId())));

        assertThat(creados(resultados)).isEqualTo(1);
        assertThat(rechazos(resultados)).hasSize(1);
        Long eventos = jdbc.queryForObject(
                "SELECT count(*) FROM evento_unidad_pendiente WHERE unidad_id = 14", Long.class);
        assertThat(eventos).isEqualTo(1);
    }

    @Test
    @DisplayName("CA-13: el índice único parcial de préstamos abiertos existe en PostgreSQL")
    void elIndiceExiste() {
        String definicion = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'ux_prestamo_unidad_abierto'", String.class);

        assertThat(definicion).contains("UNIQUE").contains("unidad_id").contains("WHERE")
                .contains("SOLICITADO").contains("ENTREGADO").doesNotContain("DEVUELTO");
    }
}
