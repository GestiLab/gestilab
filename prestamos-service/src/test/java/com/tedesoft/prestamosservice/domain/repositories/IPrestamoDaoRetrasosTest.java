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

/** SCRUM-23: la consulta solo trae los préstamos del estado pedido; la regla del retraso es del dominio. */
@DataJpaTest
@DisplayName("Integration tests (data slice) — findByEstado en IPrestamoDao con H2")
class IPrestamoDaoRetrasosTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private IPrestamoDao prestamoDao;

    private void persistir(long unidadId, EstadoPrestamo estado) {
        Prestamo p = Prestamo.solicitar(unidadId, 2L, "ana", 7, LocalDate.of(2026, 11, 2));
        ReflectionTestUtils.setField(p, "estado", estado);
        em.persist(p);
    }

    @Test
    @DisplayName("findByEstado — solo devuelve los préstamos de ese estado (CA-4)")
    void findByEstado_debeIgnorarOtrosEstados() {
        persistir(1L, EstadoPrestamo.ENTREGADO);
        persistir(2L, EstadoPrestamo.SOLICITADO);
        persistir(3L, EstadoPrestamo.DEVUELTO);
        persistir(4L, EstadoPrestamo.ENTREGADO);
        em.flush();

        assertThat(prestamoDao.findByEstado(EstadoPrestamo.ENTREGADO))
                .extracting(Prestamo::getUnidadId).containsExactlyInAnyOrder(1L, 4L);
    }
}
