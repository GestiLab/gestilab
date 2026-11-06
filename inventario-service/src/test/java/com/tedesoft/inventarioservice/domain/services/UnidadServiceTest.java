package com.tedesoft.inventarioservice.domain.services;

import com.tedesoft.inventarioservice.domain.entities.EstadoUnidad;
import com.tedesoft.inventarioservice.domain.entities.TipoEquipo;
import com.tedesoft.inventarioservice.domain.entities.Unidad;
import com.tedesoft.inventarioservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.inventarioservice.domain.exception.ReglaNegocioException;
import com.tedesoft.inventarioservice.domain.repositories.TipoEquipoRepository;
import com.tedesoft.inventarioservice.domain.repositories.UnidadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reglas del servicio aisladas con repositorios simulados: sin base de datos y sin reloj. */
@ExtendWith(MockitoExtension.class)
class UnidadServiceTest {

    @Mock
    private UnidadRepository unidadRepository;

    @Mock
    private TipoEquipoRepository tipoEquipoRepository;

    @InjectMocks
    private UnidadService service;

    private static TipoEquipo tipo(long id, String nombre) {
        TipoEquipo tipo = TipoEquipo.crear(nombre, null, 7, false);
        ReflectionTestUtils.setField(tipo, "id", id);
        return tipo;
    }

    private static Unidad unidad(long id, TipoEquipo tipo, String numero, EstadoUnidad estado) {
        Unidad unidad = Unidad.crear(tipo, numero, estado, null);
        ReflectionTestUtils.setField(unidad, "id", id);
        return unidad;
    }

    @Test
    @DisplayName("CA-1: crea la unidad ligada a su tipo y la guarda")
    void crear() {
        TipoEquipo multimetro = tipo(1L, "Multímetro");
        when(tipoEquipoRepository.findById(1L)).thenReturn(Optional.of(multimetro));
        when(unidadRepository.existsByNumeroInventarioIgnoreCase("MUL-014")).thenReturn(false);
        when(unidadRepository.save(any(Unidad.class))).then(AdditionalAnswers.returnsFirstArg());

        Unidad creada = service.crear(1L, "MUL-014", EstadoUnidad.DISPONIBLE, "Buen estado");

        assertThat(creada.getNumeroInventario()).isEqualTo("MUL-014");
        assertThat(creada.getEstado()).isEqualTo(EstadoUnidad.DISPONIBLE);
        assertThat(creada.getTipo()).isSameAs(multimetro);
        verify(unidadRepository).save(creada);
    }

    @Test
    @DisplayName("CA-2: un número de inventario repetido lanza ReglaNegocioException (409) y no guarda")
    void numeroDuplicado() {
        when(tipoEquipoRepository.findById(1L)).thenReturn(Optional.of(tipo(1L, "Multímetro")));
        when(unidadRepository.existsByNumeroInventarioIgnoreCase("MUL-014")).thenReturn(true);

        assertThatThrownBy(() -> service.crear(1L, "MUL-014", EstadoUnidad.DISPONIBLE, null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("MUL-014");

        verify(unidadRepository, never()).save(any());
    }

    @Test
    @DisplayName("CA-2: la comparación usa el número ya normalizado (sin espacios laterales)")
    void duplicadoIgnoraEspacios() {
        when(tipoEquipoRepository.findById(1L)).thenReturn(Optional.of(tipo(1L, "Multímetro")));
        when(unidadRepository.existsByNumeroInventarioIgnoreCase("MUL-014")).thenReturn(true);

        assertThatThrownBy(() -> service.crear(1L, "  MUL-014  ", EstadoUnidad.DISPONIBLE, null))
                .isInstanceOf(ReglaNegocioException.class);

        verify(unidadRepository, never()).save(any());
    }

    @Test
    @DisplayName("CA-5: un tipo inexistente lanza RecursoNoEncontradoException y aborta la creación")
    void tipoInexistente() {
        when(tipoEquipoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crear(999L, "MUL-014", EstadoUnidad.DISPONIBLE, null))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("999");

        verify(unidadRepository, never()).save(any());
    }

    @Test
    @DisplayName("CA-6: consultar un id inexistente lanza RecursoNoEncontradoException")
    void obtenerInexistente() {
        when(unidadRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerPorId(999L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Unidad 999 no encontrada");
    }

    @Test
    @DisplayName("CA-6: consultar devuelve la unidad con su tipo")
    void obtener() {
        TipoEquipo multimetro = tipo(1L, "Multímetro");
        when(unidadRepository.findById(10L))
                .thenReturn(Optional.of(unidad(10L, multimetro, "MUL-014", EstadoUnidad.DISPONIBLE)));

        Unidad encontrada = service.obtenerPorId(10L);

        assertThat(encontrada.getId()).isEqualTo(10L);
        assertThat(encontrada.getTipo()).isSameAs(multimetro);
    }

    @Test
    @DisplayName("CA-3: actualizar al estado EN_REVISION lo refleja")
    void actualizarAEnRevision() {
        TipoEquipo multimetro = tipo(1L, "Multímetro");
        when(unidadRepository.findById(10L))
                .thenReturn(Optional.of(unidad(10L, multimetro, "MUL-014", EstadoUnidad.DISPONIBLE)));
        when(tipoEquipoRepository.findById(1L)).thenReturn(Optional.of(multimetro));
        when(unidadRepository.existsByNumeroInventarioIgnoreCaseAndIdNot("MUL-014", 10L)).thenReturn(false);

        Unidad editada = service.actualizar(10L, 1L, "MUL-014", EstadoUnidad.EN_REVISION, "Calibración");

        assertThat(editada.getEstado()).isEqualTo(EstadoUnidad.EN_REVISION);
        assertThat(editada.getObservaciones()).isEqualTo("Calibración");
    }

    @Test
    @DisplayName("CA-2: editar con el número de OTRA unidad lanza ReglaNegocioException y no modifica la unidad")
    void actualizarConNumeroDeOtra() {
        TipoEquipo multimetro = tipo(1L, "Multímetro");
        Unidad existente = unidad(10L, multimetro, "MUL-014", EstadoUnidad.DISPONIBLE);
        when(unidadRepository.findById(10L)).thenReturn(Optional.of(existente));
        when(tipoEquipoRepository.findById(1L)).thenReturn(Optional.of(multimetro));
        when(unidadRepository.existsByNumeroInventarioIgnoreCaseAndIdNot("MUL-020", 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.actualizar(10L, 1L, "MUL-020", EstadoUnidad.PRESTADA, null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("MUL-020");

        assertThat(existente.getNumeroInventario()).isEqualTo("MUL-014");
        assertThat(existente.getEstado()).isEqualTo(EstadoUnidad.DISPONIBLE);
    }

    @Test
    @DisplayName("actualizar una unidad inexistente lanza RecursoNoEncontradoException")
    void actualizarInexistente() {
        when(unidadRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizar(999L, 1L, "MUL-014", EstadoUnidad.DISPONIBLE, null))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Unidad 999 no encontrada");
    }

    @Test
    @DisplayName("actualizar hacia un tipo inexistente lanza RecursoNoEncontradoException")
    void actualizarConTipoInexistente() {
        TipoEquipo multimetro = tipo(1L, "Multímetro");
        when(unidadRepository.findById(10L))
                .thenReturn(Optional.of(unidad(10L, multimetro, "MUL-014", EstadoUnidad.DISPONIBLE)));
        when(tipoEquipoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizar(10L, 999L, "MUL-014", EstadoUnidad.DISPONIBLE, null))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("999");
    }
}
