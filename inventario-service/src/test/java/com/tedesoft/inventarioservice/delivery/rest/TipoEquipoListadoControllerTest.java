package com.tedesoft.inventarioservice.delivery.rest;

import com.tedesoft.inventarioservice.domain.entities.TipoEquipo;
import com.tedesoft.inventarioservice.domain.services.TipoEquipoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.tedesoft.inventarioservice.delivery.security.ConSeguridadDePrueba;

/** CA-12: el selector de tipos devuelve la lista completa con las reglas de cada tipo. */
@ConSeguridadDePrueba
@WebMvcTest(TipoEquipoController.class)
class TipoEquipoListadoControllerTest {

    private static final String RUTA = "/api/v1/inventario-service/tipos-equipo";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TipoEquipoService service;

    private static TipoEquipo tipo(long id, String nombre, int dias, boolean autorizacion) {
        TipoEquipo tipo = TipoEquipo.crear(nombre, "Descripción de " + nombre, dias, autorizacion);
        ReflectionTestUtils.setField(tipo, "id", id);
        return tipo;
    }

    @Test
    @DisplayName("CA-12: GET devuelve todos los tipos con id, nombre, duración máxima y autorización")
    void listaCompleta() throws Exception {
        when(service.listar()).thenReturn(List.of(
                tipo(1, "Multímetro", 7, false), tipo(2, "Portátil", 1, false), tipo(3, "Cámara", 3, true)));

        mockMvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Multímetro"))
                .andExpect(jsonPath("$[0].duracionMaximaDias").value(7))
                .andExpect(jsonPath("$[0].requiereAutorizacion").value(false))
                .andExpect(jsonPath("$[2].nombre").value("Cámara"))
                .andExpect(jsonPath("$[2].requiereAutorizacion").value(true));
    }

    @Test
    @DisplayName("CA-12: sin tipos responde 200 con una lista vacía")
    void sinTipos() throws Exception {
        when(service.listar()).thenReturn(List.of());

        mockMvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
