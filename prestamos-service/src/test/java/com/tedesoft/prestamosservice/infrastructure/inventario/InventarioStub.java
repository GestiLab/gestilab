package com.tedesoft.prestamosservice.infrastructure.inventario;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tedesoft.prestamosservice.domain.models.EstadoUnidad;
import com.tedesoft.prestamosservice.domain.models.UnidadInventario;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stub del contrato {@code docs/contrato/openapi.yaml} (SCRUM-5): un servidor HTTP real y local que imita
 * {@code GET /unidades/{id}} de inventario. Permite probar el cliente sin levantar inventario (CA-6).
 *
 * <p>Ids con comportamiento fijo: 14 y 87 (200), 999 (404 del contrato) y los 5xx de abajo para fallos.
 */
final class InventarioStub implements AutoCloseable {

    static final String PREFIJO_API = "/api/v1/inventario-service";

    /** Como la unidad 14 de los datos iniciales de inventario: multímetro disponible, 7 días, sin autorización. */
    static final long ID_MULTIMETRO = 14;
    static final UnidadInventario UNIDAD_14 =
            new UnidadInventario(14L, "MUL-014", EstadoUnidad.DISPONIBLE, 7, false);

    /** Tipo distinto (3 días y autorización obligatoria): el cliente no debe conocer los tipos (RQ-01). */
    static final long ID_CAMARA = 87;
    static final UnidadInventario UNIDAD_87 =
            new UnidadInventario(87L, "CAM-007", EstadoUnidad.DISPONIBLE, 3, true);

    static final long ID_INEXISTENTE = 999;
    static final long ID_ERROR_500 = 500;
    static final long ID_JSON_ROTO = 501;
    static final long ID_ESTADO_DESCONOCIDO = 502;
    static final long ID_SIN_REGLAS = 503;
    static final long ID_403 = 504;
    static final long ID_401 = 505;
    static final long ID_400 = 506;

    private final HttpServer servidor;
    private final ExecutorService hilos = Executors.newCachedThreadPool();
    private final AtomicInteger peticiones = new AtomicInteger();
    private final Map<Long, String> cuerpos = new ConcurrentHashMap<>();
    private volatile long retardoMs;

    private InventarioStub(HttpServer servidor) {
        this.servidor = servidor;
    }

    static InventarioStub iniciar() throws IOException {
        HttpServer servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        InventarioStub stub = new InventarioStub(servidor);
        servidor.createContext(PREFIJO_API + "/unidades/", stub::atender);
        servidor.setExecutor(stub.hilos);
        servidor.start();
        return stub;
    }

    String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + PREFIJO_API;
    }

    /** Hace que el stub tarde en contestar, como un inventario colgado. */
    void retardar(long milisegundos) {
        this.retardoMs = milisegundos;
    }

    /** Hace que el stub responda 200 con este JSON exacto para ese id (tiene prioridad sobre los ids fijos). */
    void registrar(long id, String json) {
        cuerpos.put(id, json);
    }

    int peticiones() {
        return peticiones.get();
    }

    @Override
    public void close() {
        servidor.stop(0);
        hilos.shutdownNow();
    }

    private void atender(HttpExchange intercambio) throws IOException {
        peticiones.incrementAndGet();
        try {
            if (retardoMs > 0) {
                Thread.sleep(retardoMs);
            }
            String ruta = intercambio.getRequestURI().getPath();
            long id = Long.parseLong(ruta.substring(ruta.lastIndexOf('/') + 1));
            if (cuerpos.containsKey(id)) {
                responder(intercambio, 200, cuerpos.get(id));
            } else if (id == ID_MULTIMETRO) {
                responder(intercambio, 200, unidadJson(14, "MUL-014", "DISPONIBLE", 2, "Multímetro", "7", "false"));
            } else if (id == ID_CAMARA) {
                responder(intercambio, 200, unidadJson(87, "CAM-007", "DISPONIBLE", 3, "Cámara", "3", "true"));
            } else if (id == ID_ERROR_500) {
                // Cuerpo con detalles internos: el cliente no debe reenviarlos.
                responder(intercambio, 500, "{\"status\":500,\"mensaje\":\"NullPointerException en UnidadService.java:42\"}");
            } else if (id == ID_JSON_ROTO) {
                responder(intercambio, 200, "{esto no es json");
            } else if (id == ID_ESTADO_DESCONOCIDO) {
                responder(intercambio, 200, unidadJson(502, "X-502", "EXTRATERRESTRE", 2, "Raro", "7", "false"));
            } else if (id == ID_SIN_REGLAS) {
                // Falta requiereAutorizacion: no se puede asumir "false".
                responder(intercambio, 200, "{\"id\":503,\"numeroInventario\":\"X-503\",\"estado\":\"DISPONIBLE\","
                        + "\"tipo\":{\"id\":2,\"nombre\":\"Raro\",\"duracionMaximaDias\":7}}");
            } else if (id == ID_403) {
                responder(intercambio, 403, "{\"status\":403,\"mensaje\":\"Prohibido: rol auxiliar requerido\"}");
            } else if (id == ID_401) {
                responder(intercambio, 401, "{\"status\":401,\"mensaje\":\"Token ausente o vencido\"}");
            } else if (id == ID_400) {
                responder(intercambio, 400, "{\"status\":400,\"mensaje\":\"Parámetro id inválido\"}");
            } else {
                responder(intercambio, 404, "{\"status\":404,\"error\":\"Not Found\",\"mensaje\":\"Unidad " + id
                        + " no encontrada\",\"timestamp\":\"2026-11-01T10:40:29.843437\"}");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            intercambio.close();
        }
    }

    // Incluye "observaciones", un campo de más que inventario sí envía y que el cliente debe ignorar.
    private static String unidadJson(long id, String numero, String estado, long tipoId, String tipo,
                                     String dias, String autorizacion) {
        return "{\"id\":" + id + ",\"numeroInventario\":\"" + numero + "\",\"estado\":\"" + estado + "\","
                + "\"observaciones\":\"Buen estado\",\"tipo\":{\"id\":" + tipoId + ",\"nombre\":\"" + tipo + "\","
                + "\"descripcion\":\"-\",\"duracionMaximaDias\":" + dias + ",\"requiereAutorizacion\":"
                + autorizacion + "}}";
    }

    private static void responder(HttpExchange intercambio, int codigo, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(codigo, bytes.length);
        intercambio.getResponseBody().write(bytes);
    }
}
