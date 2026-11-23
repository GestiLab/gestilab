package com.tedesoft.inventarioservice.delivery.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tedesoft.inventarioservice.domain.entities.EstadoUnidad;
import com.tedesoft.inventarioservice.domain.entities.TipoEquipo;
import com.tedesoft.inventarioservice.domain.entities.Unidad;
import com.tedesoft.inventarioservice.domain.repositories.TipoEquipoRepository;
import com.tedesoft.inventarioservice.domain.repositories.UnidadRepository;
import com.tedesoft.inventarioservice.domain.services.UnidadEstadoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.tedesoft.inventarioservice.delivery.security.ConSeguridadDePrueba;

/**
 * CA-1 a CA-6, CA-9 y CA-10 contra la base H2 con el mensaje simulado, sin broker real (CA-8): el cuerpo del
 * evento entra por {@link UnidadCambioDeEstadoListener#procesar(String)}, tal como lo haría RabbitMQ. Con el
 * perfil {@code test} el listener no se registra solo, así que aquí se crea a mano.
 *
 * <p>Las anotaciones son las mismas que las de las otras pruebas de integración de unidades para compartir su
 * contexto de Spring y su base H2; cada prueba empieza y termina con la base limpia.
 */
@ConSeguridadDePrueba
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:inventario-unidades-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class UnidadEstadoEventoIntegracionTest {

    private static final String RUTA = "/api/v1/inventario-service/unidades";
    private static final String T0 = "2026-11-20T10:00:00-05:00";
    private static final String T1 = "2026-11-20T10:01:00-05:00";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UnidadEstadoService servicio;

    @Autowired
    private UnidadRepository unidadRepository;

    @Autowired
    private TipoEquipoRepository tipoEquipoRepository;

    private UnidadCambioDeEstadoListener listener;
    private TipoEquipo tipo;
    private Unidad unidad;

    @BeforeEach
    void preparar() {
        limpiar();
        listener = new UnidadCambioDeEstadoListener(objectMapper, servicio);
        tipo = tipoEquipoRepository.save(TipoEquipo.crear("Multímetro", "Mide voltaje", 7, false));
        unidad = unidadRepository.save(Unidad.crear(tipo, "MUL-014", EstadoUnidad.DISPONIBLE, null));
    }

    @AfterEach
    void limpiar() {
        unidadRepository.deleteAll();
        tipoEquipoRepository.deleteAll();
    }

    private static String evento(long unidadId, String estado, String fecha) {
        return "{\"unidadId\":" + unidadId + ",\"nuevoEstado\":\"" + estado + "\",\"fecha\":\"" + fecha + "\"}";
    }

    private Unidad guardada() {
        return unidadRepository.findById(unidad.getId()).orElseThrow();
    }

    @Test
    @DisplayName("CA-1: el evento PRESTADA deja la unidad prestada y el catálogo la muestra así")
    void quedaPrestada() throws Exception {
        listener.procesar(evento(unidad.getId(), "PRESTADA", T0));

        assertThat(guardada().getEstado()).isEqualTo(EstadoUnidad.PRESTADA);
        assertThat(guardada().getUltimoEventoEn()).isEqualTo(Instant.parse("2026-11-20T15:00:00Z"));
        mockMvc.perform(get(RUTA + "/" + unidad.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PRESTADA"));
        mockMvc.perform(get(RUTA).param("estado", "PRESTADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].numeroInventario").value("MUL-014"));
    }

    @Test
    @DisplayName("CA-2: el evento DISPONIBLE devuelve la unidad prestada a disponible")
    void quedaDisponible() throws Exception {
        listener.procesar(evento(unidad.getId(), "PRESTADA", T0));

        listener.procesar(evento(unidad.getId(), "DISPONIBLE", T1));

        assertThat(guardada().getEstado()).isEqualTo(EstadoUnidad.DISPONIBLE);
        mockMvc.perform(get(RUTA + "/" + unidad.getId()))
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"));
    }

    @Test
    @DisplayName("CA-4: el mismo evento dos veces deja el mismo resultado y no produce ningún error")
    void eventoRepetido() {
        listener.procesar(evento(unidad.getId(), "PRESTADA", T0));

        assertThatCode(() -> listener.procesar(evento(unidad.getId(), "PRESTADA", T0)))
                .doesNotThrowAnyException();

        assertThat(guardada().getEstado()).isEqualTo(EstadoUnidad.PRESTADA);
    }

    @Test
    @DisplayName("CA-5: un evento para la unidad 999 se descarta sin error y los siguientes se siguen consumiendo")
    void unidadInexistente() {
        assertThatCode(() -> listener.procesar(evento(999, "PRESTADA", T0))).doesNotThrowAnyException();

        listener.procesar(evento(unidad.getId(), "PRESTADA", T0));

        assertThat(guardada().getEstado()).isEqualTo(EstadoUnidad.PRESTADA);
        assertThat(unidadRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("CA-6: un estado que no existe se descarta y la unidad no cambia")
    void estadoInvalido() {
        listener.procesar(evento(unidad.getId(), "perdida", T0));

        assertThat(guardada().getEstado()).isEqualTo(EstadoUnidad.DISPONIBLE);
        assertThat(guardada().getUltimoEventoEn()).isNull();
    }

    @Test
    @DisplayName("CA-9: un evento anterior al último aplicado se descarta y se conserva el estado más reciente")
    void eventoFueraDeOrden() {
        listener.procesar(evento(unidad.getId(), "PRESTADA", T1));

        listener.procesar(evento(unidad.getId(), "DISPONIBLE", T0));

        assertThat(guardada().getEstado()).isEqualTo(EstadoUnidad.PRESTADA);
        assertThat(guardada().getUltimoEventoEn()).isEqualTo(Instant.parse("2026-11-20T15:01:00Z"));
    }

    @Test
    @DisplayName("CA-10: una unidad en revisión o fuera de servicio no pasa a prestada y conserva su estado")
    void unidadNoPrestable() throws Exception {
        for (EstadoUnidad noPrestable : new EstadoUnidad[]{EstadoUnidad.EN_REVISION, EstadoUnidad.FUERA_DE_SERVICIO}) {
            Unidad cargada = guardada();
            cargada.actualizar(tipo, "MUL-014", noPrestable, "Fuera del préstamo");
            unidadRepository.save(cargada);

            listener.procesar(evento(unidad.getId(), "PRESTADA", T0));

            assertThat(guardada().getEstado()).isEqualTo(noPrestable);
            mockMvc.perform(get(RUTA + "/" + unidad.getId()))
                    .andExpect(jsonPath("$.estado").value(noPrestable.name()));
        }
    }
}
