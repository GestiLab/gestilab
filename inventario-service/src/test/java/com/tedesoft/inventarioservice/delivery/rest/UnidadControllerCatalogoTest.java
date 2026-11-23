package com.tedesoft.inventarioservice.delivery.rest;

import com.tedesoft.inventarioservice.domain.entities.EstadoUnidad;
import com.tedesoft.inventarioservice.domain.entities.TipoEquipo;
import com.tedesoft.inventarioservice.domain.entities.Unidad;
import com.tedesoft.inventarioservice.domain.exception.ParametroInvalidoException;
import com.tedesoft.inventarioservice.domain.services.UnidadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.tedesoft.inventarioservice.delivery.security.ConSeguridadDePrueba;

/** Capa web del catálogo con el servicio simulado: parámetros, forma de la página y formato del 400. */
@ConSeguridadDePrueba
@WebMvcTest(UnidadController.class)
class UnidadControllerCatalogoTest {

    private static final String RUTA = "/api/v1/inventario-service/unidades";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UnidadService service;

    private static Unidad unidad(long id, String numero, EstadoUnidad estado) {
        TipoEquipo tipo = TipoEquipo.crear("Multímetro", "Mide voltaje", 7, false);
        ReflectionTestUtils.setField(tipo, "id", 1L);
        Unidad unidad = Unidad.crear(tipo, numero, estado, null);
        ReflectionTestUtils.setField(unidad, "id", id);
        return unidad;
    }

    @Test
    @DisplayName("CA-1: sin parámetros pide la página 0 de 20 y responde content, number, size, totalElements y totalPages")
    void sinParametros() throws Exception {
        when(service.listar(isNull(), isNull(), isNull(), eq(0), eq(20))).thenReturn(new PageImpl<>(
                List.of(unidad(1, "MUL-001", EstadoUnidad.DISPONIBLE), unidad(2, "MUL-002", EstadoUnidad.PRESTADA)),
                PageRequest.of(0, 20), 120));

        mockMvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].numeroInventario").value("MUL-001"))
                .andExpect(jsonPath("$.content[0].tipo.nombre").value("Multímetro"))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(120))
                .andExpect(jsonPath("$.totalPages").value(6));
    }

    @Test
    @DisplayName("CA-2 a CA-5: page, size, tipoId, estado y nombre llegan tal cual al servicio")
    void parametrosLlegan() throws Exception {
        when(service.listar(1L, "DISPONIBLE", "mult", 2, 10)).thenReturn(new PageImpl<>(
                List.of(unidad(21, "MUL-021", EstadoUnidad.DISPONIBLE)), PageRequest.of(2, 10), 21));

        mockMvc.perform(get(RUTA)
                        .param("page", "2").param("size", "10").param("tipoId", "1")
                        .param("estado", "DISPONIBLE").param("nombre", "mult"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(21));
    }

    @Test
    @DisplayName("CA-6: una página fuera de rango responde 200 con content vacío y el total real")
    void paginaFueraDeRango() throws Exception {
        when(service.listar(isNull(), isNull(), isNull(), eq(50), eq(20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(50, 20), 120));

        mockMvc.perform(get(RUTA).param("page", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.number").value(50))
                .andExpect(jsonPath("$.totalElements").value(120));
    }

    @Test
    @DisplayName("CA-8: page=-1 responde 400 con el formato común y el campo page en errores")
    void paginaNegativa() throws Exception {
        when(service.listar(any(), any(), any(), eq(-1), anyInt()))
                .thenThrow(new ParametroInvalidoException("page", "La página no puede ser negativa"));

        mockMvc.perform(get(RUTA).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.mensaje").value("Parámetros de consulta inválidos"))
                .andExpect(jsonPath("$.errores.page").value("La página no puede ser negativa"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("CA-8: size=0 responde 400 con el campo size en errores")
    void tamanoCero() throws Exception {
        when(service.listar(any(), any(), any(), anyInt(), eq(0)))
                .thenThrow(new ParametroInvalidoException("size", "El tamaño de página debe ser mayor que cero"));

        mockMvc.perform(get(RUTA).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.size").value("El tamaño de página debe ser mayor que cero"));
    }

    @Test
    @DisplayName("un valor no numérico en page responde 400 con el campo y no llega al servicio")
    void valorNoNumerico() throws Exception {
        mockMvc.perform(get(RUTA).param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensaje").value("Parámetros de consulta inválidos"))
                .andExpect(jsonPath("$.errores.page").exists());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("un id no numérico en la ruta sigue respondiendo \"Parámetro de ruta inválido\"")
    void idNoNumericoEnLaRuta() throws Exception {
        mockMvc.perform(get(RUTA + "/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Parámetro de ruta inválido"))
                .andExpect(jsonPath("$.errores.id").exists());
    }
}
