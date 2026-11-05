package com.tedesoft.inventarioservice.delivery.seed;

import com.tedesoft.inventarioservice.domain.entities.EstadoUnidad;
import com.tedesoft.inventarioservice.domain.entities.TipoEquipo;
import com.tedesoft.inventarioservice.domain.entities.Unidad;
import com.tedesoft.inventarioservice.domain.repositories.TipoEquipoRepository;
import com.tedesoft.inventarioservice.domain.repositories.UnidadRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La siembra contra una base H2 real: 3 tipos, 120 unidades y ningún duplicado al repetirla. Con el
 * perfil {@code test} el inicializador no se registra solo, así que aquí se ejecuta a mano.
 *
 * <p>Las anotaciones son las mismas que las de UnidadIntegracionTest para compartir su contexto de Spring
 * (y su base H2) en lugar de levantar otro: cada prueba deja la base vacía.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:inventario-unidades-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class DataInitializerIntegracionTest {

    @Autowired
    private TipoEquipoRepository tipoEquipoRepository;

    @Autowired
    private UnidadRepository unidadRepository;

    private DataInitializer inicializador;

    @BeforeEach
    void preparar() {
        limpiar();
        inicializador = new DataInitializer(tipoEquipoRepository, unidadRepository);
    }

    @AfterEach
    void limpiar() {
        unidadRepository.deleteAll();
        tipoEquipoRepository.deleteAll();
    }

    @Test
    @DisplayName("CA-7: con la base vacía siembra 3 tipos con reglas distintas y 120 unidades repartidas por igual")
    void siembraBaseVacia() {
        inicializador.run();

        List<TipoEquipo> tipos = tipoEquipoRepository.findAll();
        assertThat(tipos).hasSize(3);
        assertThat(tipos).extracting(TipoEquipo::getDuracionMaximaDias).containsExactlyInAnyOrder(7, 1, 3);
        assertThat(tipos).filteredOn(TipoEquipo::isRequiereAutorizacion).hasSize(1);

        List<Unidad> unidades = unidadRepository.findAll();
        assertThat(unidades).hasSize(120);
        Map<String, Long> porTipo = unidades.stream()
                .collect(Collectors.groupingBy(u -> u.getTipo().getNombre(), Collectors.counting()));
        assertThat(porTipo).hasSize(3).allSatisfy((nombre, cantidad) -> assertThat(cantidad).isEqualTo(40L));
        assertThat(unidades).extracting(Unidad::getEstado).contains(EstadoUnidad.values());
    }

    @Test
    @DisplayName("CA-8: reiniciar (ejecutar de nuevo) no duplica ni los tipos ni las unidades")
    void reinicioNoDuplica() {
        inicializador.run();
        inicializador.run();
        inicializador.run();

        assertThat(tipoEquipoRepository.count()).isEqualTo(3);
        assertThat(unidadRepository.count()).isEqualTo(120);
    }

    @Test
    @DisplayName("CA-8: una siembra a medias se completa sin duplicar lo que ya estaba")
    void completaSiembraParcial() {
        TipoEquipo multimetro = tipoEquipoRepository.save(TipoEquipo.crear("Multímetro", "ya existía", 7, false));
        unidadRepository.save(Unidad.crear(multimetro, "MUL-001", EstadoUnidad.DISPONIBLE, "ya existía"));

        inicializador.run();

        assertThat(tipoEquipoRepository.count()).isEqualTo(3);
        assertThat(unidadRepository.count()).isEqualTo(120);
        assertThat(unidadRepository.existsByNumeroInventarioIgnoreCase("MUL-001")).isTrue();
    }
}
