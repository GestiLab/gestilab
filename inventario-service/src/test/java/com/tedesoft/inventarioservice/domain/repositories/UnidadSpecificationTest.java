package com.tedesoft.inventarioservice.domain.repositories;

import com.tedesoft.inventarioservice.domain.entities.EstadoUnidad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Construcción de las especificaciones sin base de datos. La consulta real (incluida la lista vacía de
 * tipos) se prueba en UnidadCatalogoIntegracionTest; la coincidencia por nombre, en TipoEquipoBusquedaTest.
 */
class UnidadSpecificationTest {

    @Test
    @DisplayName("combinar filtros ausentes o presentes produce una especificación válida")
    void combinaFiltros() {
        assertThat(UnidadSpecification.filtrar(null, null, null)).isNotNull();
        assertThat(UnidadSpecification.filtrar(1L, EstadoUnidad.DISPONIBLE, List.of(1L, 2L))).isNotNull();
        assertThat(UnidadSpecification.filtrar(null, null, List.of())).isNotNull();
    }
}
