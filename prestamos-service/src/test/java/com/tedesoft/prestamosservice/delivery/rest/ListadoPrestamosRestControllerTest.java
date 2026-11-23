package com.tedesoft.prestamosservice.delivery.rest;

import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.services.IPrestamoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.tedesoft.prestamosservice.delivery.security.ConSeguridadDePrueba;
import com.tedesoft.prestamosservice.delivery.security.RespuestaErrorSeguridad;
import com.tedesoft.prestamosservice.delivery.security.TokensDePrueba;

/** SCRUM-24: capa web del listado con el servicio simulado. */
@ConSeguridadDePrueba
@WebMvcTest(PrestamoRestController.class)
@DisplayName("Integration tests (web slice) — GET /prestamos con filtros")
class ListadoPrestamosRestControllerTest {

    private static final String RUTA = "/api/v1/prestamos-service/prestamos";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IPrestamoService prestamoService;

    private static Prestamo prestamo(long id, String persona) {
        Prestamo p = Prestamo.solicitar(14L, 2L, persona, 7, LocalDate.of(2026, 11, 2));
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @Test
    @DisplayName("CA-4: ?persona=ana → 200 con sus préstamos")
    void listar_porPersona_debeRetornar200ConLosPrestamos() throws Exception {
        when(prestamoService.listar("ana", null)).thenReturn(List.of(prestamo(2L, "ana"), prestamo(1L, "ana")));

        mockMvc.perform(get(RUTA).param("persona", "ana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].solicitanteId").value("ana"))
                .andExpect(jsonPath("$[0].estado").value("SOLICITADO"))
                .andExpect(jsonPath("$[0].fechaDevolucionPrevista").value("2026-11-09"))
                .andExpect(jsonPath("$[1].id").value(1));
    }

    @Test
    @DisplayName("CA-6: ?estado=SOLICITADO → el servicio recibe ese estado y ninguna persona")
    void listar_porEstado_debePasarElEstadoAlServicio() throws Exception {
        when(prestamoService.listar(null, EstadoPrestamo.SOLICITADO)).thenReturn(List.of(prestamo(1L, "ana")));

        mockMvc.perform(get(RUTA).param("estado", "SOLICITADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        verify(prestamoService).listar(null, EstadoPrestamo.SOLICITADO);
    }

    /** El token de un usuario: su sub es "ana". Sin esta cabecera, estas pruebas usan el token de un auxiliar. */
    private static String comoUsuario() {
        return "Bearer " + TokensDePrueba.usuario();
    }

    @Test
    @DisplayName("CA-1: un usuario sin ?persona recibe solo los suyos (la persona sale de su token)")
    void usuario_sinPersona_veSoloLosSuyos() throws Exception {
        when(prestamoService.listar("ana", null)).thenReturn(List.of(prestamo(2L, "ana"), prestamo(1L, "ana")));

        mockMvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, comoUsuario()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].solicitanteId").value("ana"))
                .andExpect(jsonPath("$[1].solicitanteId").value("ana"));

        verify(prestamoService).listar("ana", null);
        verifyNoMoreInteractions(prestamoService);
    }

    @Test
    @DisplayName("CA-1: un usuario con ?estado filtra solo entre los suyos")
    void usuario_conEstado_filtraSoloEntreLosSuyos() throws Exception {
        when(prestamoService.listar("ana", EstadoPrestamo.SOLICITADO)).thenReturn(List.of(prestamo(1L, "ana")));

        mockMvc.perform(get(RUTA).param("estado", "SOLICITADO").header(HttpHeaders.AUTHORIZATION, comoUsuario()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(prestamoService).listar("ana", EstadoPrestamo.SOLICITADO);
        verifyNoMoreInteractions(prestamoService);
    }

    @Test
    @DisplayName("un usuario que pide ?persona= con su propio sub recibe los suyos")
    void usuario_conSuPropioSub_veLosSuyos() throws Exception {
        when(prestamoService.listar("ana", null)).thenReturn(List.of(prestamo(1L, "ana")));

        mockMvc.perform(get(RUTA).param("persona", "ana").header(HttpHeaders.AUTHORIZATION, comoUsuario()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(prestamoService).listar("ana", null);
    }

    @Test
    @DisplayName("CA-5: un usuario que pide los de otra persona recibe 403 con el formato común y sin consultar")
    void usuario_conPersonaAjena_403_sinConsultar() throws Exception {
        // Si la regla fallara, estos datos saldrían en la respuesta
        when(prestamoService.listar("luis", null)).thenReturn(List.of(prestamo(7L, "luis")));

        mockMvc.perform(get(RUTA).param("persona", "luis").header(HttpHeaders.AUTHORIZATION, comoUsuario()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.mensaje").value(RespuestaErrorSeguridad.MENSAJE_403))
                .andExpect(jsonPath("$[0]").doesNotExist());

        verifyNoInteractions(prestamoService);
    }

    @Test
    @DisplayName("CA-5: pedir una persona ajena da la misma respuesta exista o no, para no revelar nada")
    void usuario_personaAjena_mismaRespuestaExistaONo() throws Exception {
        when(prestamoService.listar("luis", null)).thenReturn(List.of(prestamo(7L, "luis")));

        String conPrestamos = mockMvc.perform(get(RUTA).param("persona", "luis")
                        .header(HttpHeaders.AUTHORIZATION, comoUsuario()))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String inexistente = mockMvc.perform(get(RUTA).param("persona", "nadie")
                        .header(HttpHeaders.AUTHORIZATION, comoUsuario()))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertEquals(conPrestamos.replaceAll("\"timestamp\":\"[^\"]*\"", ""),
                inexistente.replaceAll("\"timestamp\":\"[^\"]*\"", ""),
                "persona con préstamos y persona inexistente deben ser indistinguibles");
        verifyNoInteractions(prestamoService);
    }

    @Test
    @DisplayName("un token sin un sub utilizable no identifica a nadie → 403 y no consulta")
    void usuario_tokenSinSubUtilizable_403_sinConsultar() throws Exception {
        mockMvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + TokensDePrueba.valido(" ", "usuario")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(prestamoService);
    }

    @Test
    @DisplayName("sin parámetros → 200 con todos")
    void listar_sinParametros_debePedirTodos() throws Exception {
        when(prestamoService.listar(null, null)).thenReturn(List.of(prestamo(1L, "ana")));

        mockMvc.perform(get(RUTA)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("CA-7: persona sin préstamos → 200 con lista vacía")
    void listar_sinResultados_debeRetornar200ConListaVacia() throws Exception {
        when(prestamoService.listar("nadie", null)).thenReturn(List.of());

        mockMvc.perform(get(RUTA).param("persona", "nadie"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @ParameterizedTest(name = "persona inválida «{0}» → 400 sin llamar al servicio")
    @ValueSource(strings = {"", "   ", " ", "an\u0000a"})
    void listar_personaInvalida_debeRetornar400(String persona) throws Exception {
        mockMvc.perform(get(RUTA).param("persona", persona))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Datos de entrada inválidos"))
                .andExpect(jsonPath("$.errores.persona").isNotEmpty());

        verifyNoInteractions(prestamoService);
    }

    @Test
    @DisplayName("se valida después de recortar, igual que al solicitar: 100 caracteres y un espacio es válido")
    void listar_personaEnElLimiteConEspacio_debeAceptarseRecortada() throws Exception {
        String cien = "a".repeat(100);
        when(prestamoService.listar(cien, null)).thenReturn(List.of());

        mockMvc.perform(get(RUTA).param("persona", cien + " ")).andExpect(status().isOk());

        verify(prestamoService).listar(cien, null);
    }

    @Test
    @DisplayName("un salto de línea al final se recorta, igual que al solicitar")
    void listar_personaConSaltoDeLineaFinal_debeAceptarseRecortada() throws Exception {
        when(prestamoService.listar("ana", null)).thenReturn(List.of());

        mockMvc.perform(get(RUTA).param("persona", "ana\n")).andExpect(status().isOk());

        verify(prestamoService).listar("ana", null);
    }

    @Test
    @DisplayName("persona de 101 caracteres → 400")
    void listar_personaMuyLarga_debeRetornar400() throws Exception {
        mockMvc.perform(get(RUTA).param("persona", "a".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.persona").isNotEmpty());

        verifyNoInteractions(prestamoService);
    }

    @ParameterizedTest(name = "estado inválido «{0}» → 400")
    @ValueSource(strings = {"ENTREGADA", "solicitado", "RETRASADO", "1"})
    void listar_estadoInvalido_debeRetornar400(String estado) throws Exception {
        mockMvc.perform(get(RUTA).param("estado", estado)).andExpect(status().isBadRequest());

        verifyNoInteractions(prestamoService);
    }
}
