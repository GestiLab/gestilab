package com.tedesoft.prestamosservice.domain.repositories;

import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** SCRUM-24: las consultas de listado filtran y ordenan con el más reciente primero. */
@DataJpaTest
@DisplayName("Integration tests (data slice) — listados en IPrestamoDao con H2")
class IPrestamoDaoListadoTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private IPrestamoDao prestamoDao;

    private Prestamo persistir(long unidadId, String persona, EstadoPrestamo estado) {
        Prestamo p = Prestamo.solicitar(unidadId, 2L, persona, 7, LocalDate.of(2026, 11, 2));
        ReflectionTestUtils.setField(p, "estado", estado);
        return em.persist(p);
    }

    private void datos() {
        persistir(1L, "ana", EstadoPrestamo.DEVUELTO);
        persistir(2L, "luis", EstadoPrestamo.SOLICITADO);
        persistir(3L, "ana", EstadoPrestamo.SOLICITADO);
        persistir(4L, "ana", EstadoPrestamo.ENTREGADO);
        em.flush();
    }

    @Test
    @DisplayName("por persona — trae solo los de esa persona, el más reciente primero (CA-1, CA-4)")
    void porPersona_debeIgnorarALosDemasYOrdenarDescendente() {
        datos();

        assertThat(prestamoDao.findBySolicitanteIdOrderByIdDesc("ana"))
                .extracting(Prestamo::getUnidadId).containsExactly(4L, 3L, 1L);
    }

    @Test
    @DisplayName("por estado — trae solo los de ese estado, de cualquier persona (CA-6)")
    void porEstado_debeTraerSoloEseEstado() {
        datos();

        assertThat(prestamoDao.findByEstadoOrderByIdDesc(EstadoPrestamo.SOLICITADO))
                .extracting(Prestamo::getUnidadId).containsExactly(3L, 2L);
    }

    @Test
    @DisplayName("por persona y estado — combina los dos filtros")
    void porPersonaYEstado_debeCombinar() {
        datos();

        assertThat(prestamoDao.findBySolicitanteIdAndEstadoOrderByIdDesc("ana", EstadoPrestamo.SOLICITADO))
                .extracting(Prestamo::getUnidadId).containsExactly(3L);
    }

    @Test
    @DisplayName("todos — sin filtros, el más reciente primero")
    void todos_debeOrdenarDescendente() {
        datos();

        assertThat(prestamoDao.findAllByOrderByIdDesc())
                .extracting(Prestamo::getUnidadId).containsExactly(4L, 3L, 2L, 1L);
    }

    @Test
    @DisplayName("persona sin préstamos — lista vacía (CA-7)")
    void personaSinPrestamos_debeDevolverVacio() {
        datos();

        assertThat(prestamoDao.findBySolicitanteIdOrderByIdDesc("nadie")).isEmpty();
    }
}
