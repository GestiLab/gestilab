package com.tedesoft.inventarioservice.delivery.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-20 en inventario-service. Aquí NO se usa el token por defecto de las demás pruebas: cada petición dice
 * exactamente qué token lleva (o ninguno).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(DecodificadorDePruebaConfig.class)
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:inventario-seguridad;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@DisplayName("SCRUM-20: token y roles en inventario-service (401 y 403)")
class AutorizacionInventarioTest {

    private static final String BASE = "/api/v1/inventario-service";

    /** Los 8 endpoints del servicio, con el rol que los alcanza. */
    private record Endpoint(HttpMethod metodo, String ruta, boolean soloAuxiliar) {
        String etiqueta() {
            return metodo + " " + ruta;
        }
    }

    private static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint(HttpMethod.GET, BASE + "/tipos-equipo", false),
            new Endpoint(HttpMethod.GET, BASE + "/tipos-equipo/1", false),
            new Endpoint(HttpMethod.GET, BASE + "/unidades", false),
            new Endpoint(HttpMethod.GET, BASE + "/unidades/1", false),
            new Endpoint(HttpMethod.POST, BASE + "/tipos-equipo", true),
            new Endpoint(HttpMethod.PUT, BASE + "/tipos-equipo/1", true),
            new Endpoint(HttpMethod.POST, BASE + "/unidades", true),
            new Endpoint(HttpMethod.PUT, BASE + "/unidades/1", true));

    private static final String PERMITIDO = "permitido";

    private record Fila(String operacion, String ruta, String rol, String esperado, String obtenido, int codigo) {
        boolean coincide() {
            return esperado.equals(obtenido);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private ListAppender<ILoggingEvent> lineas;
    private Logger registro;

    @BeforeEach
    void capturarRegistro() {
        registro = (Logger) LoggerFactory.getLogger(RegistroAccesoRechazado.NOMBRE_REGISTRO);
        lineas = new ListAppender<>();
        lineas.start();
        registro.addAppender(lineas);
    }

    @AfterEach
    void soltarRegistro() {
        registro.detachAppender(lineas);
    }

    private static MockHttpServletRequestBuilder peticion(Endpoint e, String token) {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.request(e.metodo(), e.ruta())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
        if (token != null) {
            b.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return b;
    }

    private int codigo(Endpoint e, String token) throws Exception {
        return mockMvc.perform(peticion(e, token)).andReturn().getResponse().getStatus();
    }

    private static Stream<Arguments> todosLosEndpoints() {
        return ENDPOINTS.stream().map(e -> Arguments.of(e.etiqueta(), e));
    }

    private static Stream<Arguments> soloAuxiliar() {
        return ENDPOINTS.stream().filter(Endpoint::soloAuxiliar).map(e -> Arguments.of(e.etiqueta(), e));
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
    }

    @Test
    @DisplayName("CA-2/CA-11: token alterado, vencido, de otro emisor, sin firma o con otra clave → 401")
    void tokensInvalidos_responden401() throws Exception {
        Endpoint e = ENDPOINTS.get(0);
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
    }

    // ---- 403 ----

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
        assertFalse(cuerpo.contains("nombre") || cuerpo.contains("codigo") || cuerpo.contains("estado"),
                "el 403 no debe traer datos del recurso");
    }

    @Test
    @DisplayName("RQ-03: el 403 llega aunque el cuerpo sea inválido (la regla se aplica antes de validarlo)")
    void usuarioConCuerpoInvalido_recibe403_noRecibe400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post(BASE + "/tipos-equipo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TokensDePrueba.usuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un token válido sin ninguno de los dos roles → 403")
    void tokenSinRoles_responde403() throws Exception {
        mockMvc.perform(peticion(ENDPOINTS.get(0), TokensDePrueba.valido("sin-rol")))
                .andExpect(status().isForbidden());
    }

    // ---- permitidos ----

    @Test
    @DisplayName("CA-4/CA-5: el auxiliar usa los endpoints de auxiliar; el usuario consulta el catálogo")
    void rolesCorrectos_noSonRechazados() throws Exception {
        for (Endpoint e : ENDPOINTS) {
            int auxiliar = codigo(e, TokensDePrueba.auxiliar());
            assertTrue(auxiliar != 401 && auxiliar != 403, e.etiqueta() + " con auxiliar dio " + auxiliar);
            if (!e.soloAuxiliar()) {
                int usuario = codigo(e, TokensDePrueba.usuario());
                assertTrue(usuario != 401 && usuario != 403, e.etiqueta() + " con usuario dio " + usuario);
            }
        }
    }

    // ---- registro ----

    @Test
    @DisplayName("CA-7/CA-12: cada 401 y 403 deja una línea con fecha, sujeto, método, ruta y rol, sin el token")
    void rechazos_quedanRegistradosSinElToken() throws Exception {
        String tokenUsuario = TokensDePrueba.usuario();
        Endpoint restringido = ENDPOINTS.get(4);

        mockMvc.perform(peticion(restringido, tokenUsuario)).andExpect(status().isForbidden());
        mockMvc.perform(peticion(restringido, null)).andExpect(status().isUnauthorized());

        assertEquals(2, lineas.list.size());
        String linea403 = lineas.list.get(0).getFormattedMessage();
        assertTrue(linea403.contains("estado=403"), linea403);
        assertTrue(linea403.contains("sujeto=" + TokensDePrueba.SUJETO_USUARIO), linea403);
        assertTrue(linea403.contains("metodo=POST"), linea403);
        assertTrue(linea403.contains("ruta=" + restringido.ruta()), linea403);
        assertTrue(linea403.contains("rol=usuario"), linea403);
        assertTrue(linea403.contains("fecha="), linea403);
        assertTrue(linea403.contains("motivo="), linea403);
        String linea401 = lineas.list.get(1).getFormattedMessage();
        assertTrue(linea401.contains("estado=401"), linea401);
        assertTrue(linea401.contains("ruta=" + restringido.ruta()), linea401);
        for (ILoggingEvent evento : lineas.list) {
            assertFalse(evento.getFormattedMessage().contains(tokenUsuario), "el token nunca se registra");
            assertFalse(evento.getFormattedMessage().contains("eyJ"), "ningún fragmento de token en el registro");
        }
    }

    // ---- matriz ----

    @Test
    @DisplayName("CA-10: peticiones de la matriz que viven en este servicio (operación × rol), esperado vs obtenido")
    void matriz_esperadoContraObtenido() throws Exception {
        String usuario = TokensDePrueba.usuario();
        String auxiliar = TokensDePrueba.auxiliar();
        Endpoint catalogo = ENDPOINTS.get(2);     // GET unidades
        Endpoint altaDeTipo = ENDPOINTS.get(4);   // POST tipos-equipo (el alta de unidad es la misma restricción)
        Endpoint editarReglas = ENDPOINTS.get(5); // PUT tipos-equipo/{id}

        List<Fila> filas = new ArrayList<>();
        filas.add(fila("Consultar el catálogo", "usuario", catalogo, usuario, PERMITIDO));
        filas.add(fila("Consultar el catálogo", "auxiliar", catalogo, auxiliar, PERMITIDO));
        filas.add(fila("Dar de alta un tipo o una unidad", "usuario", altaDeTipo, usuario, "403"));
        filas.add(fila("Dar de alta un tipo o una unidad", "auxiliar", altaDeTipo, auxiliar, PERMITIDO));
        filas.add(fila("Editar las reglas de un tipo", "usuario", editarReglas, usuario, "403"));
        filas.add(fila("Editar las reglas de un tipo", "auxiliar", editarReglas, auxiliar, PERMITIDO));

        StringBuilder md = new StringBuilder("# Matriz de autorización — inventario-service (SCRUM-20)\n\n")
                .append("Parte de la matriz del enunciado (pág. 8) que vive en este servicio: 3 operaciones × 2 roles = ")
                .append(filas.size()).append(" de las 16 peticiones. Las otras 10 están en `prestamos-service`.\n\n")
                .append("\"permitido\" = pasó la seguridad; la respuesta real depende de los datos de la prueba.\n\n")
                .append("| Operación | Ruta | Rol | Esperado | Obtenido | Código | OK |\n|---|---|---|---|---|---|---|\n");
        for (Fila f : filas) {
            md.append("| ").append(f.operacion()).append(" | ").append(f.ruta()).append(" | ").append(f.rol())
                    .append(" | ").append(f.esperado()).append(" | ").append(f.obtenido()).append(" | ")
                    .append(f.codigo()).append(" | ").append(f.coincide() ? "sí" : "NO").append(" |\n");
        }
        escribirInforme("informe-autorizacion-inventario.md", md.toString());

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

    private static void escribirInforme(String nombre, String contenido) throws IOException {
        Path destino = Path.of("target", nombre);
        Files.createDirectories(destino.getParent());
        Files.writeString(destino, contenido, StandardCharsets.UTF_8);
    }
}
