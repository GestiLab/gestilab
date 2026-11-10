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

@DataJpaTest
@DisplayName("Integration tests (data slice) — IPrestamoDao con H2")
class IPrestamoDaoTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private IPrestamoDao prestamoDao;

    @Test
    @DisplayName("save — persiste el préstamo con su duración y fechas")
    void save_debePersistirElPrestamo() {
        Prestamo nuevo = Prestamo.solicitar(14L, 2L, "ana", 7, LocalDate.of(2026, 11, 2));

        Prestamo guardado = prestamoDao.save(nuevo);
        em.flush();
        em.clear();

        Prestamo leido = prestamoDao.findById(guardado.getId()).orElseThrow();
        assertThat(leido.getUnidadId()).isEqualTo(14L);
        assertThat(leido.getTipoId()).isEqualTo(2L);
        assertThat(leido.getEstado()).isEqualTo(EstadoPrestamo.SOLICITADO);
        assertThat(leido.getDuracionAplicadaDias()).isEqualTo(7);
        assertThat(leido.getFechaPrevista()).isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    @DisplayName("existsByUnidadIdAndEstadoIn — solo cuenta préstamos activos de esa unidad")
    void existeActivoDeLaUnidad_debeIgnorarDevueltosYOtrasUnidades() {
        LocalDate hoy = LocalDate.of(2026, 11, 2);
        Prestamo devuelto = Prestamo.solicitar(14L, 2L, "ana", 7, hoy);
        ReflectionTestUtils.setField(devuelto, "estado", EstadoPrestamo.DEVUELTO);
        em.persist(devuelto);
        em.persist(Prestamo.solicitar(15L, 2L, "ana", 7, hoy));
        em.flush();

        assertThat(prestamoDao.existsByUnidadIdAndEstadoIn(14L, EstadoPrestamo.activos())).isFalse();
        assertThat(prestamoDao.existsByUnidadIdAndEstadoIn(15L, EstadoPrestamo.activos())).isTrue();

        Prestamo nuevo = Prestamo.solicitar(14L, 2L, "luis", 7, hoy);
        em.persist(nuevo);
        em.flush();

        assertThat(prestamoDao.existsByUnidadIdAndEstadoIn(14L, EstadoPrestamo.activos())).isTrue();
    }

    @Test
    @DisplayName("countBySolicitanteIdAndEstadoIn — cuenta solo los activos de esa persona")
    void contarActivos_debeIgnorarDevueltosYOtrasPersonas() {
        LocalDate hoy = LocalDate.of(2026, 11, 2);
        em.persist(Prestamo.solicitar(1L, 2L, "ana", 7, hoy));
        Prestamo entregado = Prestamo.solicitar(2L, 2L, "ana", 7, hoy);
        ReflectionTestUtils.setField(entregado, "estado", EstadoPrestamo.ENTREGADO);
        em.persist(entregado);
        Prestamo devuelto = Prestamo.solicitar(3L, 2L, "ana", 7, hoy);
        ReflectionTestUtils.setField(devuelto, "estado", EstadoPrestamo.DEVUELTO);
        em.persist(devuelto);
        em.persist(Prestamo.solicitar(4L, 2L, "luis", 7, hoy));
        em.flush();

        long activos = prestamoDao.countBySolicitanteIdAndEstadoIn("ana", EstadoPrestamo.activos());

        assertThat(activos).isEqualTo(2);
    }
}
