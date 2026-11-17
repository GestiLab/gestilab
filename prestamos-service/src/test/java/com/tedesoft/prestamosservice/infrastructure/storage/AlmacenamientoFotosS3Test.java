package com.tedesoft.prestamosservice.infrastructure.storage;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tedesoft.prestamosservice.domain.clients.IAlmacenamientoFotos;
import com.tedesoft.prestamosservice.domain.exception.DependenciaNoDisponibleException;
import com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El adaptador real de S3 contra un servidor HTTP mínimo del propio JDK que imita lo que S3 responde (sin
 * Docker, así corre también en el CI). Cubre CA-7 de SCRUM-22: almacenamiento caído, que no responde, o con
 * error, responde un error controlado y rápido.
 */
class AlmacenamientoFotosS3Test {

    private static final String BUCKET = "fotos-prueba";
    private static final byte[] FOTO = {1, 2, 3, 4, 5, 6, 7, 8};

    private HttpServer servidor;
    private String endpoint;
    private final Map<String, byte[]> objetos = new ConcurrentHashMap<>();
    private final List<String> peticiones = new CopyOnWriteArrayList<>();
    private volatile int retardoMs = 0;
    private volatile int estadoForzado = 0;
    private String idAnterior;
    private String claveAnterior;

    @BeforeEach
    void arrancarServidorFalso() throws IOException {
        // Credenciales falsas: el adaptador usa la cadena por defecto de AWS (variables, propiedades, perfil...)
        idAnterior = System.getProperty("aws.accessKeyId");
        claveAnterior = System.getProperty("aws.secretAccessKey");
        System.setProperty("aws.accessKeyId", "prueba");
        System.setProperty("aws.secretAccessKey", "prueba");

        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.setExecutor(Executors.newCachedThreadPool());
        servidor.createContext("/", this::atender);
        servidor.start();
        endpoint = "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @AfterEach
    void detenerServidorFalso() {
        servidor.stop(0);
        restaurar("aws.accessKeyId", idAnterior);
        restaurar("aws.secretAccessKey", claveAnterior);
    }

    private static void restaurar(String propiedad, String valor) {
        if (valor == null) {
            System.clearProperty(propiedad);
        } else {
            System.setProperty(propiedad, valor);
        }
    }

    private void atender(HttpExchange intercambio) throws IOException {
        peticiones.add(intercambio.getRequestMethod() + " " + intercambio.getRequestURI().getPath());
        try {
            if (retardoMs > 0) {
                Thread.sleep(retardoMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String ruta = intercambio.getRequestURI().getPath();
        byte[] cuerpoPeticion = intercambio.getRequestBody().readAllBytes();
        if (estadoForzado != 0) {
            responder(intercambio, estadoForzado, "<Error><Code>InternalError</Code></Error>".getBytes(StandardCharsets.UTF_8), null);
            return;
        }
        switch (intercambio.getRequestMethod()) {
            case "PUT" -> {
                objetos.put(ruta, cuerpoPeticion);
                intercambio.getResponseHeaders().add("ETag", "\"x\"");
                responder(intercambio, 200, new byte[0], null);
            }
            case "GET" -> {
                byte[] guardado = objetos.get(ruta);
                if (guardado == null) {
                    responder(intercambio, 404,
                            "<Error><Code>NoSuchKey</Code><Message>no existe</Message></Error>"
                                    .getBytes(StandardCharsets.UTF_8), "application/xml");
                } else {
                    responder(intercambio, 200, guardado, "image/png");
                }
            }
            case "DELETE" -> {
                objetos.remove(ruta);
                responder(intercambio, 204, new byte[0], null);
            }
            default -> responder(intercambio, 405, new byte[0], null);
        }
    }

    private static void responder(HttpExchange intercambio, int estado, byte[] cuerpo, String tipo) throws IOException {
        if (tipo != null) {
            intercambio.getResponseHeaders().add("Content-Type", tipo);
        }
        intercambio.sendResponseHeaders(estado, estado == 204 ? -1 : cuerpo.length == 0 ? -1 : cuerpo.length);
        if (cuerpo.length > 0 && estado != 204) {
            intercambio.getResponseBody().write(cuerpo);
        }
        intercambio.close();
    }

    private AlmacenamientoFotosS3 almacenamiento(String endpointUsado, int timeoutSegundos) {
        return new AlmacenamientoFotosS3(BUCKET, "us-east-1", endpointUsado, timeoutSegundos);
    }

    @Test
    @DisplayName("guardar sube el objeto con una clave aleatoria bajo prestamos/fotos/ y recuperar lo devuelve")
    void guardarYRecuperar() {
        AlmacenamientoFotosS3 s3 = almacenamiento(endpoint, 5);

        String clave = s3.guardar(FOTO, "image/png");
        String otra = s3.guardar(FOTO, "image/png");

        assertThat(clave).matches("prestamos/fotos/[0-9a-f-]{36}").isNotEqualTo(otra);
        assertThat(objetos).containsKey("/" + BUCKET + "/" + clave);
        IAlmacenamientoFotos.Foto foto = s3.recuperar(clave);
        assertThat(foto.tipoMime()).isEqualTo("image/png");
        assertThat(foto.contenido()).isNotEmpty();
        s3.cerrar();
    }

    @Test
    @DisplayName("eliminar borra el objeto; una clave en blanco no hace nada")
    void eliminar() {
        AlmacenamientoFotosS3 s3 = almacenamiento(endpoint, 5);
        String clave = s3.guardar(FOTO, "image/png");

        s3.eliminar(clave);
        s3.eliminar(" ");

        assertThat(objetos).doesNotContainKey("/" + BUCKET + "/" + clave);
        s3.cerrar();
    }

    @Test
    @DisplayName("recuperar una clave que no existe lanza RecursoNoEncontradoException (404)")
    void recuperarInexistente() {
        AlmacenamientoFotosS3 s3 = almacenamiento(endpoint, 5);

        assertThatThrownBy(() -> s3.recuperar("prestamos/fotos/no-existe"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> s3.recuperar(" "))
                .isInstanceOf(RecursoNoEncontradoException.class);
        s3.cerrar();
    }

    @Test
    @DisplayName("CA-7: con el almacenamiento caído (conexión rechazada) lanza DependenciaNoDisponibleException")
    void almacenamientoCaido() {
        servidor.stop(0);
        AlmacenamientoFotosS3 s3 = almacenamiento(endpoint, 5);

        assertThatThrownBy(() -> s3.guardar(FOTO, "image/png"))
                .isInstanceOf(DependenciaNoDisponibleException.class)
                .hasMessageContaining("almacenamiento de fotografías");
        assertThatThrownBy(() -> s3.recuperar("prestamos/fotos/x"))
                .isInstanceOf(DependenciaNoDisponibleException.class);
        s3.cerrar();
    }

    @Test
    @DisplayName("CA-7: si el almacenamiento no responde, falla con el tiempo de espera y no se cuelga")
    void almacenamientoQueNoResponde() {
        retardoMs = 20_000;
        AlmacenamientoFotosS3 s3 = almacenamiento(endpoint, 1);

        long inicio = System.nanoTime();
        assertThatThrownBy(() -> s3.guardar(FOTO, "image/png"))
                .isInstanceOf(DependenciaNoDisponibleException.class);
        long segundos = (System.nanoTime() - inicio) / 1_000_000_000L;

        assertThat(segundos).as("segundos hasta fallar").isLessThan(8);
        s3.cerrar();
    }

    @Test
    @DisplayName("CA-7: si el almacenamiento responde con error 5xx lanza DependenciaNoDisponibleException")
    void almacenamientoConError() {
        estadoForzado = 500;
        AlmacenamientoFotosS3 s3 = almacenamiento(endpoint, 3);

        assertThatThrownBy(() -> s3.guardar(FOTO, "image/png"))
                .isInstanceOf(DependenciaNoDisponibleException.class);
        s3.cerrar();
    }

    @Test
    @DisplayName("el adaptador no valida la foto otra vez: eso ya lo hizo el servicio")
    void noRevalidaLaFoto() {
        AlmacenamientoFotosS3 s3 = almacenamiento(endpoint, 5);

        // bytes que no son una imagen: la validación es del servicio (ValidadorFotos), no del adaptador
        assertThat(s3.guardar(new byte[]{9, 9, 9}, "image/png")).startsWith("prestamos/fotos/");
        s3.cerrar();
    }

    @Test
    @DisplayName("sin bucket configurado no arranca")
    void sinBucket() {
        assertThatThrownBy(() -> new AlmacenamientoFotosS3(" ", "us-east-1", endpoint, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucket");
    }
}
