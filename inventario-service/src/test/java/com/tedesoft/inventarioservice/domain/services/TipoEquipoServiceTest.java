package com.tedesoft.inventarioservice.domain.services;

import com.tedesoft.inventarioservice.domain.entities.TipoEquipo;
import com.tedesoft.inventarioservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.inventarioservice.domain.repositories.TipoEquipoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
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

@ExtendWith(MockitoExtension.class)
class TipoEquipoServiceTest {

    @Mock
    private TipoEquipoRepository repository;

    @InjectMocks
    private TipoEquipoService service;

    @Test
    @DisplayName("crear persiste los datos y reglas tal cual llegan, sin lógica por tipo")
    void crearPersisteLasReglas() {
        when(repository.save(any(TipoEquipo.class))).thenAnswer(inv -> {
            TipoEquipo guardado = inv.getArgument(0);
            ReflectionTestUtils.setField(guardado, "id", 1L);
            return guardado;
        });

        TipoEquipo respuesta = service.crear("Cámara", "Cámara réflex", 3, true);

        ArgumentCaptor<TipoEquipo> captor = ArgumentCaptor.forClass(TipoEquipo.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getNombre()).isEqualTo("Cámara");
        assertThat(captor.getValue().getDuracionMaximaDias()).isEqualTo(3);
        assertThat(captor.getValue().isRequiereAutorizacion()).isTrue();
        assertThat(respuesta.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("actualizar cambia la duración a 3 días y la devuelve")
    void actualizarCambiaLaDuracion() {
        TipoEquipo existente = TipoEquipo.crear("Multímetro", "desc", 7, false);
        ReflectionTestUtils.setField(existente, "id", 5L);
        when(repository.findById(5L)).thenReturn(Optional.of(existente));
        when(repository.save(any(TipoEquipo.class))).then(AdditionalAnswers.returnsFirstArg());

        TipoEquipo respuesta = service.actualizar(5L, "Multímetro", "desc", 3, false);

        assertThat(respuesta.getDuracionMaximaDias()).isEqualTo(3);
        assertThat(respuesta.getId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("consultar un id inexistente lanza RecursoNoEncontradoException con el id")
    void obtenerInexistente() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerPorId(999L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Tipo de equipo 999 no encontrado");
    }

    @Test
    @DisplayName("editar un id inexistente lanza la excepción y no guarda nada")
    void actualizarInexistente() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizar(999L, "X", null, 2, false))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(repository, never()).save(any());
    }
}
