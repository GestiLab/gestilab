package com.tedesoft.inventario.service;

import com.tedesoft.inventario.domain.TipoEquipo;
import com.tedesoft.inventario.dto.TipoEquipoRequestDto;
import com.tedesoft.inventario.dto.TipoEquipoResponseDto;
import com.tedesoft.inventario.exception.TipoEquipoNoEncontradoException;
import com.tedesoft.inventario.repository.TipoEquipoRepository;
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
class TipoEquipoServiceImplTest {

    @Mock
    private TipoEquipoRepository repository;

    @InjectMocks
    private TipoEquipoServiceImpl service;

    @Test
    @DisplayName("CA-1/CA-2/CA-6: crear persiste los datos y reglas tal cual llegan, sin lógica por tipo")
    void crearPersisteLasReglas() {
        when(repository.save(any(TipoEquipo.class))).thenAnswer(inv -> {
            TipoEquipo guardado = inv.getArgument(0);
            ReflectionTestUtils.setField(guardado, "id", 1L);
            return guardado;
        });

        TipoEquipoResponseDto respuesta =
                service.crear(new TipoEquipoRequestDto("Cámara", "Cámara réflex", 3, true));

        ArgumentCaptor<TipoEquipo> captor = ArgumentCaptor.forClass(TipoEquipo.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getNombre()).isEqualTo("Cámara");
        assertThat(captor.getValue().getDuracionMaximaDias()).isEqualTo(3);
        assertThat(captor.getValue().isRequiereAutorizacionPrevia()).isTrue();
        assertThat(respuesta).isEqualTo(new TipoEquipoResponseDto(1L, "Cámara", "Cámara réflex", 3, true));
    }

    @Test
    @DisplayName("CA-3: actualizar cambia la duración a 3 días y la devuelve")
    void actualizarCambiaLaDuracion() {
        TipoEquipo existente = TipoEquipo.crear("Multímetro", "desc", 7, false);
        ReflectionTestUtils.setField(existente, "id", 5L);
        when(repository.findById(5L)).thenReturn(Optional.of(existente));
        when(repository.save(any(TipoEquipo.class))).then(AdditionalAnswers.returnsFirstArg());

        TipoEquipoResponseDto respuesta =
                service.actualizar(5L, new TipoEquipoRequestDto("Multímetro", "desc", 3, false));

        assertThat(respuesta.duracionMaximaDias()).isEqualTo(3);
        assertThat(respuesta.id()).isEqualTo(5L);
    }

    @Test
    @DisplayName("CA-5: consultar un id inexistente lanza TipoEquipoNoEncontradoException")
    void obtenerInexistente() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerPorId(999L))
                .isInstanceOf(TipoEquipoNoEncontradoException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("CA-5: editar un id inexistente lanza la excepción y no guarda nada")
    void actualizarInexistente() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizar(999L, new TipoEquipoRequestDto("X", null, 2, false)))
                .isInstanceOf(TipoEquipoNoEncontradoException.class);

        verify(repository, never()).save(any());
    }
}
