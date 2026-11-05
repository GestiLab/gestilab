package com.tedesoft.inventarioservice.delivery.seed;

import com.tedesoft.inventarioservice.domain.entities.EstadoUnidad;
import com.tedesoft.inventarioservice.domain.entities.TipoEquipo;
import com.tedesoft.inventarioservice.domain.entities.Unidad;
import com.tedesoft.inventarioservice.domain.repositories.TipoEquipoRepository;
import com.tedesoft.inventarioservice.domain.repositories.UnidadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CA-7 y CA-8 aislados con repositorios simulados: qué se siembra y cuándo se omite. */
@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private TipoEquipoRepository tipoEquipoRepository;

    @Mock
    private UnidadRepository unidadRepository;

    @Captor
    private ArgumentCaptor<List<Unidad>> unidadesGuardadas;

    @InjectMocks
    private DataInitializer inicializador;

    private static List<String> numerosEsperados() {
        List<String> numeros = new ArrayList<>();
        for (String prefijo : List.of("MUL", "POR", "CAM")) {
            for (int n = 1; n <= 40; n++) {
                numeros.add(String.format(Locale.ROOT, "%s-%03d", prefijo, n));
            }
        }
        return numeros;
    }

    @Test
    @DisplayName("CA-7: con la base vacía crea 3 tipos con reglas distintas y 120 unidades (40 por tipo)")
    void siembraBaseVacia() {
        when(tipoEquipoRepository.findAll()).thenReturn(List.of());
        when(unidadRepository.findAllNumerosInventario()).thenReturn(List.of());
        when(tipoEquipoRepository.save(any(TipoEquipo.class))).then(AdditionalAnswers.returnsFirstArg());

        inicializador.run();

        ArgumentCaptor<TipoEquipo> tipos = ArgumentCaptor.forClass(TipoEquipo.class);
        verify(tipoEquipoRepository, times(3)).save(tipos.capture());
        assertThat(tipos.getAllValues()).extracting(TipoEquipo::getDuracionMaximaDias)
                .containsExactlyInAnyOrder(7, 1, 3);
        assertThat(tipos.getAllValues()).filteredOn(TipoEquipo::isRequiereAutorizacion).hasSize(1);

        verify(unidadRepository, times(3)).saveAll(unidadesGuardadas.capture());
        List<Unidad> todas = unidadesGuardadas.getAllValues().stream().flatMap(List::stream).toList();
        assertThat(todas).hasSize(120);
        assertThat(todas).extracting(Unidad::getNumeroInventario).containsExactlyInAnyOrderElementsOf(numerosEsperados());
        assertThat(todas).extracting(Unidad::getEstado).contains(EstadoUnidad.values());
        assertThat(unidadesGuardadas.getAllValues()).allSatisfy(lote -> assertThat(lote).hasSize(40));
    }

    @Test
    @DisplayName("CA-8: si los 3 tipos y las 120 unidades ya existen, no guarda nada")
    void omiteSiYaExisten() {
        when(tipoEquipoRepository.findAll()).thenReturn(List.of(
                TipoEquipo.crear("Multímetro", null, 7, false),
                TipoEquipo.crear("Portátil", null, 1, false),
                TipoEquipo.crear("Cámara", null, 3, true)));
        when(unidadRepository.findAllNumerosInventario()).thenReturn(numerosEsperados());

        inicializador.run();

        verify(tipoEquipoRepository, never()).save(any());
        verify(unidadRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("CA-8: el número ya existente se reconoce sin distinguir mayúsculas y solo se crean las que faltan")
    void completaLoQueFalta() {
        List<String> existentes = new ArrayList<>(numerosEsperados());
        existentes.remove("CAM-040");
        existentes.set(existentes.indexOf("MUL-001"), "mul-001");
        when(tipoEquipoRepository.findAll()).thenReturn(List.of(
                TipoEquipo.crear("multímetro", null, 7, false),
                TipoEquipo.crear("Portátil", null, 1, false),
                TipoEquipo.crear("Cámara", null, 3, true)));
        when(unidadRepository.findAllNumerosInventario()).thenReturn(existentes);

        inicializador.run();

        verify(tipoEquipoRepository, never()).save(any());
        verify(unidadRepository).saveAll(unidadesGuardadas.capture());
        assertThat(unidadesGuardadas.getValue()).extracting(Unidad::getNumeroInventario)
                .containsExactly("CAM-040");
    }
}
