package com.tedesoft.prestamosservice.delivery.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tedesoft.prestamosservice.delivery.rest.PrestamoRestController;
import com.tedesoft.prestamosservice.domain.clients.IAlmacenamientoFotos;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.prestamosservice.domain.services.IPrestamoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-20 en prestamos-service: roles por endpoint, 401, 403, propiedad de los préstamos, registro y matriz.
 * No usa el token por defecto de las otras pruebas: cada petición dice qué token lleva (o ninguno).
 */
@WebMvcTest(PrestamoRestController.class)
@Import({SeguridadConfig.class, DecodificadorDePruebaConfig.class})
@DisplayName("SCRUM-20: token y roles en prestamos-service (401 y 403)")
class AutorizacionPrestamosTest {

    private static final String BASE = "/api/v1/prestamos-service/prestamos";

    private static final String PERMITIDO = "permitido";

    /** Quién alcanza cada endpoint. */
    private enum Quien { AMBOS, SOLO_USUARIO, SOLO_AUXILIAR }

    /** Los 8 endpoints del servicio, con el rol que los alcanza. */
    private record Endpoint(HttpMethod metodo, String ruta, Quien quien) {
        String etiqueta() {
            return metodo + " " + ruta;
        }
    }

    private static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint(HttpMethod.POST, BASE, Quien.SOLO_USUARIO),
            new Endpoint(HttpMethod.GET, BASE, Quien.AMBOS),
            new Endpoint(HttpMethod.GET, BASE + "/1", Quien.AMBOS),
            new Endpoint(HttpMethod.GET, BASE + "/1/foto-entrega", Quien.SOLO_AUXILIAR),
            new Endpoint(HttpMethod.GET, BASE + "/1/foto-devolucion", Quien.SOLO_AUXILIAR),
            new Endpoint(HttpMethod.GET, BASE + "/retrasos", Quien.SOLO_AUXILIAR),
            new Endpoint(HttpMethod.PATCH, BASE + "/1/entregar", Quien.SOLO_AUXILIAR),
            new Endpoint(HttpMethod.PATCH, BASE + "/1/recibir", Quien.SOLO_AUXILIAR));

    private record Fila(String operacion, String ruta, String rol, String esperado, String obtenido, int codigo) {
        boolean coincide() {
            return esperado.equals(obtenido);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IPrestamoService prestamoService;

    private ListAppender<ILoggingEvent> lineas;
    private Logger registro;

    @BeforeEach
    void preparar() {
        registro = (Logger) LoggerFactory.getLogger(RegistroAccesoRechazado.NOMBRE_REGISTRO);
        lineas = new ListAppender<>();
        lineas.start();
        registro.addAppender(lineas);

        // Préstamo 1 es de "ana" (sujeto del token de usuario); el 99 no existe.
        when(prestamoService.obtenerPorId(1L)).thenReturn(prestamo(1L, TokensDePrueba.SUJETO_USUARIO));
        when(prestamoService.obtenerPorId(99L)).thenThrow(new RecursoNoEncontradoException("Préstamo 99 no existe"));
        when(prestamoService.obtenerFotoEntrega(anyLong()))
                .thenReturn(new IAlmacenamientoFotos.Foto(new byte[]{1, 2, 3}, "image/png"));
        when(prestamoService.obtenerFotoDevolucion(anyLong()))
                .thenReturn(new IAlmacenamientoFotos.Foto(new byte[]{1, 2, 3}, "image/png"));
        when(prestamoService.solicitar(anyLong(), any()))
                .thenReturn(prestamo(1L, TokensDePrueba.SUJETO_USUARIO));
    }

    @AfterEach
    void soltarRegistro() {
        registro.detachAppender(lineas);
    }

    private static Prestamo prestamo(long id, String persona) {
        Prestamo p = Prestamo.solicitar(14L, 2L, persona, 7, LocalDate.of(2026, 11, 2));
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private static MockHttpServletRequestBuilder peticion(HttpMethod metodo, String ruta, String token) {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.request(metodo, ruta)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
        if (token != null) {
            b.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return b;
    }

    private static MockHttpServletRequestBuilder peticion(Endpoint e, String token) {
        return peticion(e.metodo(), e.ruta(), token);
    }

    private int codigo(Endpoint e, String token) throws Exception {
        return mockMvc.perform(peticion(e, token)).andReturn().getResponse().getStatus();
    }

    private static Stream<Arguments> todosLosEndpoints() {
        return ENDPOINTS.stream().map(e -> Arguments.of(e.etiqueta(), e));
    }

    private static Stream<Arguments> soloAuxiliar() {
        return ENDPOINTS.stream().filter(e -> e.quien() == Quien.SOLO_AUXILIAR)
                .map(e -> Arguments.of(e.etiqueta(), e));
    }

    private static Stream<Arguments> soloUsuario() {
        return ENDPOINTS.stream().filter(e -> e.quien() == Quien.SOLO_USUARIO)
                .map(e -> Arguments.of(e.etiqueta(), e));
    }

    // ---- 401 ----

    @ParameterizedTest(name = "CA-1: sin token → 401 en {0}")
    @MethodSource("todosLosEndpoints")
    void sinToken_responde401ConFormatoComun(String nombre, Endpoint e) throws Exception {
        mockMvc.perform(peticion(e, null))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.mensaje").value(RespuestaErrorSeguridad.MENSAJE_401))
                .andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(prestamoService);
    }

    @Test
    @DisplayName("CA-2/CA-11: token alterado, vencido, de otro emisor, sin firma o con otra clave → 401")
    void tokensInvalidos_responden401() throws Exception {
        Endpoint e = ENDPOINTS.get(1);
        List<String> invalidos = List.of(
                TokensDePrueba.alterado(), TokensDePrueba.vencido(), TokensDePrueba.deOtroEmisor(),
                TokensDePrueba.sinFirma(), TokensDePrueba.firmadoConOtraClave(), "esto-no-es-un-token");
        for (String token : invalidos) {
            mockMvc.perform(peticion(e, token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.mensaje").value(RespuestaErrorSeguridad.MENSAJE_401));
        }
        assertEquals(invalidos.size(), lineas.list.size(), "cada rechazo deja una línea en el registro");
        verifyNoInteractions(prestamoService);
    }

    // ---- 403 por rol ----

    @ParameterizedTest(name = "CA-3/CA-9: usuario en {0} → 403")
    @MethodSource("soloAuxiliar")
    void usuarioEnEndpointDeAuxiliar_responde403SinDatos(String nombre, Endpoint e) throws Exception {
        MvcResult resultado = mockMvc.perform(peticion(e, TokensDePrueba.usuario()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.mensaje").value(RespuestaErrorSeguridad.MENSAJE_403))
                .andReturn();
        String cuerpo = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(cuerpo.contains("solicitanteId") || cuerpo.contains("diasRetraso"),
                "el 403 no debe traer datos de préstamos");
        verifyNoInteractions(prestamoService);
    }

    @ParameterizedTest(name = "CA-9: auxiliar en {0} → 403 (la matriz niega solicitar al auxiliar)")
    @MethodSource("soloUsuario")
    void auxiliarEnEndpointDeUsuario_responde403SinDatos(String nombre, Endpoint e) throws Exception {
        MvcResult resultado = mockMvc.perform(peticion(e, TokensDePrueba.auxiliar()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.mensaje").value(RespuestaErrorSeguridad.MENSAJE_403))
                .andReturn();
        String cuerpo = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(cuerpo.contains("solicitanteId") || cuerpo.contains("unidadId"),
                "el 403 no debe traer datos de préstamos");
        verifyNoInteractions(prestamoService);
    }

    @Test
    @DisplayName("RQ-03: el 403 llega aunque el cuerpo sea inválido (la regla se aplica antes de validarlo)")
    void auxiliarConCuerpoInvalido_recibe403_noRecibe400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post(BASE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TokensDePrueba.auxiliar())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unidadId\":-5}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(prestamoService);
    }

    @Test
    @DisplayName("CA-13: retrasos → usuario 403 sin datos; auxiliar 200")
    void retrasos_usuario403_auxiliar200() throws Exception {
        mockMvc.perform(peticion(HttpMethod.GET, BASE + "/retrasos", TokensDePrueba.usuario()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(RespuestaErrorSeguridad.MENSAJE_403));
        verify(prestamoService, never()).listarRetrasados();

        mockMvc.perform(peticion(HttpMethod.GET, BASE + "/retrasos", TokensDePrueba.auxiliar()))
                .andExpect(status().isOk());
        verify(prestamoService).listarRetrasados();
    }

    @Test
    @DisplayName("Un token válido sin ninguno de los dos roles → 403")
    void tokenSinRoles_responde403() throws Exception {
        mockMvc.perform(peticion(ENDPOINTS.get(1), TokensDePrueba.valido("sin-rol")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(prestamoService);
    }

    // ---- CA-5: lo que el usuario sí puede ----

    @Test
    @DisplayName("CA-5: el usuario solicita una unidad (201) y consulta su propio préstamo (200)")
    void usuario_puedeSolicitarYVerLoSuyo() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post(BASE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TokensDePrueba.usuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unidadId\":14,\"solicitanteId\":\"" + TokensDePrueba.SUJETO_USUARIO + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(peticion(HttpMethod.GET, BASE + "/1", TokensDePrueba.usuario()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solicitanteId").value(TokensDePrueba.SUJETO_USUARIO));
    }

    // ---- CA-6: préstamos ajenos ----

    @Test
    @DisplayName("CA-6: préstamo de otra persona y préstamo inexistente dan el mismo 403 al usuario")
    void usuario_prestamoAjenoOInexistente_mismoCuerpo403() throws Exception {
        String otro = TokensDePrueba.valido("luis", "usuario");

        String ajeno = mockMvc.perform(peticion(HttpMethod.GET, BASE + "/1", otro))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String inexistente = mockMvc.perform(peticion(HttpMethod.GET, BASE + "/99", otro))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertFalse(ajeno.contains("ana") || ajeno.contains("14"), "no debe filtrar datos del préstamo");
        assertEquals(
                ajeno.replaceAll("\"timestamp\":\"[^\"]*\"", ""),
                inexistente.replaceAll("\"timestamp\":\"[^\"]*\"", ""),
                "ajeno e inexistente deben ser indistinguibles");
    }

    @Test
    @DisplayName("CA-6: el auxiliar sí ve cualquier préstamo, y un inexistente le da 404")
    void auxiliar_veCualquierPrestamo_yInexistente404() throws Exception {
        mockMvc.perform(peticion(HttpMethod.GET, BASE + "/1", TokensDePrueba.auxiliar()))
                .andExpect(status().isOk());
        mockMvc.perform(peticion(HttpMethod.GET, BASE + "/99", TokensDePrueba.auxiliar()))
                .andExpect(status().isNotFound());
    }

    // ---- registro ----

    @Test
    @DisplayName("CA-7/CA-12: cada 401 y 403 (también el decidido en el controlador) deja una línea sin el token")
    void rechazos_quedanRegistradosSinElToken() throws Exception {
        String tokenUsuario = TokensDePrueba.usuario();

        mockMvc.perform(peticion(HttpMethod.GET, BASE + "/retrasos", tokenUsuario)).andExpect(status().isForbidden());
        mockMvc.perform(peticion(HttpMethod.GET, BASE + "/retrasos", null)).andExpect(status().isUnauthorized());
        mockMvc.perform(peticion(HttpMethod.GET, BASE + "/99", tokenUsuario)).andExpect(status().isForbidden());

        assertEquals(3, lineas.list.size());
        String porRol = lineas.list.get(0).getFormattedMessage();
        assertTrue(porRol.contains("estado=403"), porRol);
        assertTrue(porRol.contains("sujeto=" + TokensDePrueba.SUJETO_USUARIO), porRol);
        assertTrue(porRol.contains("metodo=GET"), porRol);
        assertTrue(porRol.contains("ruta=" + BASE + "/retrasos"), porRol);
        assertTrue(porRol.contains("rol=usuario"), porRol);
        assertTrue(porRol.contains("fecha="), porRol);
        assertTrue(porRol.contains("motivo="), porRol);
        assertTrue(lineas.list.get(1).getFormattedMessage().contains("estado=401"));
        String porPropiedad = lineas.list.get(2).getFormattedMessage();
        assertTrue(porPropiedad.contains("estado=403") && porPropiedad.contains("ruta=" + BASE + "/99"), porPropiedad);
        for (ILoggingEvent evento : lineas.list) {
            assertFalse(evento.getFormattedMessage().contains(tokenUsuario), "el token nunca se registra");
            assertFalse(evento.getFormattedMessage().contains("eyJ"), "ningún fragmento de token en el registro");
        }
    }

    // ---- matriz ----

    @Test
    @DisplayName("CA-10: peticiones de la matriz que viven en este servicio (operación × rol), esperado vs obtenido")
    void matriz_esperadoContraObtenido() throws Exception {
        String ana = TokensDePrueba.usuario();                    // solicitante del préstamo 1
        String luis = TokensDePrueba.valido("luis", "usuario");   // otra persona
        String auxiliar = TokensDePrueba.auxiliar();
        Endpoint solicitar = ENDPOINTS.get(0);
        Endpoint verUno = ENDPOINTS.get(2);
        Endpoint entregar = ENDPOINTS.get(6);
        Endpoint recibir = ENDPOINTS.get(7);

        List<Fila> filas = new ArrayList<>();
        filas.add(fila("Solicitar una unidad", "usuario", solicitar, ana, PERMITIDO));
        filas.add(fila("Solicitar una unidad", "auxiliar", solicitar, auxiliar, "403"));
        filas.add(fila("Ver préstamos propios", "usuario", verUno, ana, PERMITIDO));
        filas.add(fila("Ver préstamos propios", "auxiliar", verUno, auxiliar, PERMITIDO));
        filas.add(fila("Ver préstamos ajenos", "usuario", verUno, luis, "403"));
        filas.add(fila("Ver préstamos ajenos", "auxiliar", verUno, auxiliar, PERMITIDO));
        filas.add(fila("Entregar una unidad", "usuario", entregar, ana, "403"));
        filas.add(fila("Entregar una unidad", "auxiliar", entregar, auxiliar, PERMITIDO));
        filas.add(fila("Registrar la devolución", "usuario", recibir, ana, "403"));
        filas.add(fila("Registrar la devolución", "auxiliar", recibir, auxiliar, PERMITIDO));

        StringBuilder md = new StringBuilder("# Matriz de autorización — prestamos-service (SCRUM-20)\n\n")
                .append("Parte de la matriz del enunciado (pág. 8) que vive en este servicio: 5 operaciones × 2 roles = ")
                .append(filas.size()).append(" de las 16 peticiones. Las otras 6 están en `inventario-service`.\n\n")
                .append("\"permitido\" = pasó la seguridad; la respuesta real depende de los datos de la prueba. ")
                .append("\"Ver propios\" y \"ver ajenos\" usan la misma ruta con un usuario que es o no es el solicitante.\n\n")
                .append("| Operación | Ruta | Rol | Esperado | Obtenido | Código | OK |\n|---|---|---|---|---|---|---|\n");
        for (Fila f : filas) {
            md.append("| ").append(f.operacion()).append(" | ").append(f.ruta()).append(" | ").append(f.rol())
                    .append(" | ").append(f.esperado()).append(" | ").append(f.obtenido()).append(" | ")
                    .append(f.codigo()).append(" | ").append(f.coincide() ? "sí" : "NO").append(" |\n");
        }
        escribir(Path.of("target", "informe-autorizacion-prestamos.md"), md.toString());

        List<String> fallos = filas.stream().filter(f -> !f.coincide())
                .map(f -> f.operacion() + " con " + f.rol() + ": esperado " + f.esperado()
                        + ", obtenido " + f.obtenido() + " (" + f.codigo() + ")")
                .toList();
        assertTrue(fallos.isEmpty(), "Diferencias: " + fallos);
    }

    private Fila fila(String operacion, String rol, Endpoint e, String token, String esperado) throws Exception {
        int codigo = codigo(e, token);
        String obtenido = codigo == 401 || codigo == 403 ? String.valueOf(codigo) : PERMITIDO;
        return new Fila(operacion, e.etiqueta(), rol, esperado, obtenido, codigo);
    }

    private static void escribir(Path destino, String contenido) throws IOException {
        Files.createDirectories(destino.getParent());
        Files.writeString(destino, contenido, StandardCharsets.UTF_8);
    }
}
