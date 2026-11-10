package com.tedesoft.prestamosservice.infrastructure.inventario;

import com.tedesoft.prestamosservice.config.InventarioClientConfig;
import com.tedesoft.prestamosservice.config.InventarioClientProperties;
import com.tedesoft.prestamosservice.domain.exception.DependenciaNoDisponibleException;
import com.tedesoft.prestamosservice.domain.exception.DependenciaRechazadaException;
import com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.prestamosservice.domain.models.EstadoUnidad;
import com.tedesoft.prestamosservice.domain.models.UnidadInventario;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import java.util.Map;
import static com.tedesoft.prestamosservice.infrastructure.inventario.InventarioStub.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CA-1 a CA-4 y CA-6: el cliente real contra un stub HTTP del contrato, sin inventario levantado.
 * El {@link RestClient} se arma con la misma configuración de producción ({@link InventarioClientConfig}).
 */
class InventarioHttpClientTest {

    private static final Duration TIMEOUT_CORTO = Duration.ofMillis(300);

    private InventarioStub stub;

    @BeforeEach
    void iniciarStub() throws Exception {
        stub = InventarioStub.iniciar();
    }

    @AfterEach
    void detenerStub() {
        stub.close();
    }

    private static InventarioHttpClient clienteHacia(String url, Duration timeout) {
        RestClient restClient = new InventarioClientConfig()
                .inventarioRestClient(RestClient.builder(), new InventarioClientProperties(url, timeout));
        return new InventarioHttpClient(restClient);
    }

    private InventarioHttpClient cliente() {
        return clienteHacia(stub.url(), Duration.ofSeconds(3));
    }

    // ---- CA-1 · Lectura correcta

    @Test
    void ca1_unidadDisponible_devuelveEstadoYReglasDelTipo() {
        var unidad = cliente().obtenerUnidad(ID_MULTIMETRO);

        assertThat(unidad).isEqualTo(UNIDAD_14);
        assertThat(unidad.estaDisponible()).isTrue();
        assertThat(unidad.duracionMaximaDias()).isEqualTo(7);
        assertThat(unidad.requiereAutorizacion()).isFalse();
    }

    @Test
    void ca1_otroTipoConOtrasReglas_seLeeIgualSinConocerElTipo() {
        // RQ-01: un tipo con 3 días y autorización llega por la misma llamada, sin cambiar el cliente.
        assertThat(cliente().obtenerUnidad(ID_CAMARA)).isEqualTo(UNIDAD_87);
    }

    // ---- CA-2 · Unidad inexistente

    @Test
    void ca2_inventarioResponde404_lanzaUnidadNoExiste() {
        assertThatThrownBy(() -> cliente().obtenerUnidad(ID_INEXISTENTE))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Unidad 999 no encontrada");
    }

    // ---- CA-3 · Inventario caído

    @Test
    void ca3_inventarioApagado_lanzaDependenciaNoDisponible() throws Exception {
        String urlDeServicioApagado;
        try (InventarioStub apagado = InventarioStub.iniciar()) {
            urlDeServicioApagado = apagado.url();
        } // al cerrar, nadie escucha en ese puerto

        assertThatThrownBy(() -> clienteHacia(urlDeServicioApagado, Duration.ofSeconds(3)).obtenerUnidad(ID_MULTIMETRO))
                .isInstanceOf(DependenciaNoDisponibleException.class)
                .hasMessage(InventarioHttpClient.MENSAJE_NO_DISPONIBLE);
    }

    @Test
    void ca3_inventarioResponde5xx_lanzaDependenciaNoDisponibleSinReenviarSuCuerpo() {
        assertThatThrownBy(() -> cliente().obtenerUnidad(ID_ERROR_500))
                .isInstanceOf(DependenciaNoDisponibleException.class)
                .hasMessage(InventarioHttpClient.MENSAJE_NO_DISPONIBLE)
                .hasMessageNotContaining("NullPointerException");
    }

    // ---- 4xx distinto de 404: inventario sí respondió, pero rechazó la petición de préstamos.
    //      Es un problema de integración (credenciales, contrato), no una caída: 502, no 503.

    @ParameterizedTest(name = "inventario responde {0}")
    @ValueSource(longs = {ID_401, ID_403, ID_400})
    void inventarioRechazaLaPeticion_lanzaDependenciaRechazadaSinReenviarSuCuerpo(long id) {
        assertThatThrownBy(() -> cliente().obtenerUnidad(id))
                .isInstanceOf(DependenciaRechazadaException.class)
                .isNotInstanceOf(DependenciaNoDisponibleException.class)
                .hasMessage(InventarioHttpClient.MENSAJE_RECHAZO)
                .hasMessageNotContaining("Token")
                .hasMessageNotContaining("rol auxiliar");
    }

    @Test
    void ca3_cuerpoQueNoCumpleElContrato_seTrataComoDependenciaNoDisponible() {
        InventarioHttpClient cliente = cliente();

        assertThatThrownBy(() -> cliente.obtenerUnidad(ID_JSON_ROTO))
                .isInstanceOf(DependenciaNoDisponibleException.class);
        assertThatThrownBy(() -> cliente.obtenerUnidad(ID_ESTADO_DESCONOCIDO))
                .isInstanceOf(DependenciaNoDisponibleException.class);
        // Sin requiereAutorizacion no se asume "false": una regla ausente no es una regla permisiva.
        assertThatThrownBy(() -> cliente.obtenerUnidad(ID_SIN_REGLAS))
                .isInstanceOf(DependenciaNoDisponibleException.class);
    }

    @Test
    void ca1_elEjemploDelContrato_seLeeConElClienteReal() throws Exception {
        // Si alguien cambia el contrato (campos, tipos, nombres), esta prueba lo detecta del lado del cliente.
        Map<String, Object> ejemplo = ContratoOpenApi.ejemploDeUnidad();
        long id = ((Number) ejemplo.get("id")).longValue();
        stub.registrar(id, new ObjectMapper().writeValueAsString(ejemplo));
        @SuppressWarnings("unchecked")
        Map<String, Object> tipo = (Map<String, Object>) ejemplo.get("tipo");

        UnidadInventario esperada = new UnidadInventario(id, (String) ejemplo.get("numeroInventario"),
                EstadoUnidad.valueOf((String) ejemplo.get("estado")), ((Number) tipo.get("id")).longValue(),
                ((Number) tipo.get("duracionMaximaDias")).intValue(), (Boolean) tipo.get("requiereAutorizacion"));

        assertThat(cliente().obtenerUnidad(id)).isEqualTo(esperada);
    }

    @Test
    void ca3_sinNumeroDeInventario_seTrataComoDependenciaNoDisponible() {
        // numeroInventario es obligatorio en el contrato.
        stub.registrar(600, "{\"id\":600,\"estado\":\"DISPONIBLE\",\"tipo\":{\"id\":2,\"nombre\":\"X\","
                + "\"duracionMaximaDias\":7,\"requiereAutorizacion\":false}}");

        assertThatThrownBy(() -> cliente().obtenerUnidad(600L))
                .isInstanceOf(DependenciaNoDisponibleException.class);
    }

    @Test
    void ca3_sinIdDelTipo_seTrataComoDependenciaNoDisponible() {
        // El préstamo guarda el tipo (RF-09, RF-11): sin tipo.id no se puede crear.
        stub.registrar(601, "{\"id\":601,\"numeroInventario\":\"X-601\",\"estado\":\"DISPONIBLE\","
                + "\"tipo\":{\"nombre\":\"X\",\"duracionMaximaDias\":7,\"requiereAutorizacion\":false}}");

        assertThatThrownBy(() -> cliente().obtenerUnidad(601L))
                .isInstanceOf(DependenciaNoDisponibleException.class);
    }

    // ---- CA-4 · Sin esperas infinitas

    @Test
    void ca4_inventarioTardaMasQueElTimeout_cortaYAplicaElComportamientoDelCa3() {
        stub.retardar(2_000);
        InventarioHttpClient cliente = clienteHacia(stub.url(), TIMEOUT_CORTO);

        long inicio = System.nanoTime();
        assertThatThrownBy(() -> cliente.obtenerUnidad(ID_MULTIMETRO))
                .isInstanceOf(DependenciaNoDisponibleException.class)
                .hasMessage(InventarioHttpClient.MENSAJE_NO_DISPONIBLE);
        Duration transcurrido = Duration.ofNanos(System.nanoTime() - inicio);

        // Cortó a los ~300 ms. El tope es el retardo del stub (2 s): holgura para un CI lento sin
        // dejar de detectar un cliente que espera la respuesta completa.
        assertThat(transcurrido).isLessThan(Duration.ofMillis(2_000));
    }

    @Test
    void ca4_siInventarioContestaDentroDelTimeout_noSeCorta() {
        stub.retardar(100);

        assertThat(clienteHacia(stub.url(), Duration.ofSeconds(3)).obtenerUnidad(ID_MULTIMETRO))
                .isEqualTo(UNIDAD_14);
    }
}
