package com.tedesoft.inventarioservice.domain.services;

import com.tedesoft.inventarioservice.domain.entities.Unidad;
import com.tedesoft.inventarioservice.domain.exception.ParametroInvalidoException;
import com.tedesoft.inventarioservice.domain.repositories.TipoEquipoRepository;
import com.tedesoft.inventarioservice.domain.repositories.UnidadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Reglas del listado del catálogo aisladas con repositorios simulados: sin base de datos ni reloj. */
@ExtendWith(MockitoExtension.class)
class UnidadServiceCatalogoTest {

    @Mock
    private UnidadRepository unidadRepository;

    @Mock
    private TipoEquipoRepository tipoEquipoRepository;

    @InjectMocks
    private UnidadService service;

    @Captor
    private ArgumentCaptor<Pageable> pageable;

    @SuppressWarnings("unchecked")
    private void repositorioDevuelve(Page<Unidad> pagina) {
        when(unidadRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pagina);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("CA-10: pide la página y el tamaño indicados, siempre ordenada por id ascendente")
    void ordenaPorIdAscendente() {
        repositorioDevuelve(new PageImpl<>(List.of()));

        service.listar(null, null, null, 2, 20);

        verify(unidadRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Test
    @DisplayName("devuelve la página que entrega el repositorio")
    void devuelveLaPagina() {
        Page<Unidad> esperada = new PageImpl<>(List.of());
        repositorioDevuelve(esperada);

        assertThat(service.listar(1L, "DISPONIBLE", "mult", 0, 20)).isSameAs(esperada);
    }

    @Test
    @DisplayName("CA-8: una página negativa lanza ParametroInvalidoException y no consulta la base")
    void paginaNegativa() {
        assertThatThrownBy(() -> service.listar(null, null, null, -1, 20))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasFieldOrPropertyWithValue("campo", "page");

        verifyNoInteractions(unidadRepository);
    }

    @ParameterizedTest(name = "size = {0}")
    @ValueSource(ints = {0, -1, -20})
    @DisplayName("CA-8: un tamaño menor o igual que cero lanza ParametroInvalidoException y no consulta la base")
    void tamanoInvalido(int size) {
        assertThatThrownBy(() -> service.listar(null, null, null, 0, size))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasFieldOrPropertyWithValue("campo", "size");

        verifyNoInteractions(unidadRepository);
    }

    @Test
    @DisplayName("CA-8: un tamaño mayor que 100 lanza ParametroInvalidoException y no consulta la base")
    void tamanoMayorQueElTope() {
        assertThatThrownBy(() -> service.listar(null, null, null, 0, 101))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasFieldOrPropertyWithValue("campo", "size")
                .hasMessageContaining("100");

        verifyNoInteractions(unidadRepository);
    }

    @Test
    @DisplayName("un tamaño de exactamente 100 se acepta")
    void tamanoEnElTope() {
        repositorioDevuelve(new PageImpl<>(List.of()));

        assertThat(service.listar(null, null, null, 0, 100)).isNotNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("CA-6: una página tan lejana que desborda el desplazamiento responde vacía con el total real")
    void paginaQueDesbordaElDesplazamiento() {
        when(unidadRepository.count(any(Specification.class))).thenReturn(120L);

        Page<Unidad> pagina = service.listar(null, null, null, Integer.MAX_VALUE, 20);

        assertThat(pagina.getContent()).isEmpty();
        assertThat(pagina.getTotalElements()).isEqualTo(120);
        assertThat(pagina.getNumber()).isEqualTo(Integer.MAX_VALUE);
        verify(unidadRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("un estado desconocido lanza ParametroInvalidoException y lista los permitidos")
    void estadoDesconocido() {
        assertThatThrownBy(() -> service.listar(null, "perdida", null, 0, 20))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasFieldOrPropertyWithValue("campo", "estado")
                .hasMessageContaining("DISPONIBLE")
                .hasMessageContaining("FUERA_DE_SERVICIO");

        verifyNoInteractions(unidadRepository);
    }

    @Test
    @DisplayName("un estado en blanco equivale a no filtrar por estado")
    void estadoEnBlancoNoFiltra() {
        repositorioDevuelve(new PageImpl<>(List.of()));

        assertThat(service.listar(null, "   ", null, 0, 20).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("CA-4: sin búsqueda por nombre no se consultan los tipos")
    void sinNombreNoConsultaTipos() {
        repositorioDevuelve(new PageImpl<>(List.of()));

        service.listar(null, null, "  ", 0, 20);

        verifyNoInteractions(tipoEquipoRepository);
    }

    @Test
    @DisplayName("CA-4: con búsqueda por nombre se comparan los tipos en el dominio")
    void conNombreConsultaTipos() {
        repositorioDevuelve(new PageImpl<>(List.of()));

        service.listar(null, null, "multi", 0, 20);

        verify(tipoEquipoRepository).findAll();
    }

    @Test
    @DisplayName("el estado se acepta en minúsculas")
    void estadoEnMinusculas() {
        repositorioDevuelve(new PageImpl<>(List.of()));

        assertThat(service.listar(null, "disponible", null, 0, 20)).isNotNull();
    }
}
