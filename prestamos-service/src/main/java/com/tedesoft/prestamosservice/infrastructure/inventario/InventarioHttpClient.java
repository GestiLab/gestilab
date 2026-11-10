package com.tedesoft.prestamosservice.infrastructure.inventario;

import com.tedesoft.prestamosservice.domain.clients.IInventarioClient;
import com.tedesoft.prestamosservice.domain.exception.DependenciaNoDisponibleException;
import com.tedesoft.prestamosservice.domain.exception.DependenciaRechazadaException;
import com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.prestamosservice.domain.models.EstadoUnidad;
import com.tedesoft.prestamosservice.domain.models.UnidadInventario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente HTTP de {@code GET /unidades/{id}} de inventario (contrato: {@code docs/contrato/openapi.yaml}).
 * Solo habla HTTP: jamás toca la base de datos de inventario.
 *
 * <p>Traducción de resultados:
 * <ul>
 *   <li>200 con cuerpo válido → {@link UnidadInventario}.</li>
 *   <li>404 → {@link RecursoNoEncontradoException} (el handler global responde 404).</li>
 *   <li>Otro 4xx (400, 401, 403...) → {@link DependenciaRechazadaException} (el handler global responde 502
 *       sin reenviar el cuerpo): inventario respondió, pero rechazó la petición.</li>
 *   <li>Sin respuesta, tiempo agotado, 5xx o cuerpo que no cumple el contrato →
 *       {@link DependenciaNoDisponibleException} (el handler global responde 503 sin reenviar el cuerpo).</li>
 * </ul>
 */
@Component
public class InventarioHttpClient implements IInventarioClient {

    static final String MENSAJE_NO_DISPONIBLE = "El servicio de inventario no está disponible. Intenta más tarde.";
    static final String MENSAJE_RECHAZO = "No se pudo consultar el servicio de inventario. Contacta al administrador.";

    private static final Logger log = LoggerFactory.getLogger(InventarioHttpClient.class);

    // Formas del JSON de inventario. Campos de más (observaciones, descripcion...) se ignoran;
    // los campos del contrato son envoltorios para detectar los que falten en vez de asumir 0/false.
    record UnidadJson(Long id, String numeroInventario, EstadoUnidad estado, TipoJson tipo) { }
    record TipoJson(Long id, String nombre, Integer duracionMaximaDias, Boolean requiereAutorizacion) { }

    private final RestClient restClient;

    public InventarioHttpClient(@Qualifier("inventarioRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public UnidadInventario obtenerUnidad(Long id) {
        try {
            UnidadJson unidad = restClient.get()
                    .uri("/unidades/{id}", id)
                    .retrieve()
                    // Un solo manejador para todos los 4xx: el orden entre varios onStatus no queda a la vista.
                    .onStatus(estado -> estado.is4xxClientError(), (peticion, respuesta) -> {
                        if (respuesta.getStatusCode().value() == 404) {
                            throw new RecursoNoEncontradoException("Unidad " + id + " no encontrada");
                        }
                        // Inventario respondió pero rechazó la petición (credenciales, permisos, contrato).
                        log.warn("Inventario rechazó la consulta de la unidad {} con {}", id, respuesta.getStatusCode());
                        throw new DependenciaRechazadaException(MENSAJE_RECHAZO);
                    })
                    .body(UnidadJson.class);
            return aDominio(id, unidad);
        } catch (RestClientException e) {
            // Sin conexión, timeout, 5xx o JSON ilegible: el detalle solo va al log.
            log.warn("Inventario no disponible al consultar la unidad {}: {}", id, e.getMessage());
            throw new DependenciaNoDisponibleException(MENSAJE_NO_DISPONIBLE, e);
        }
    }

    private UnidadInventario aDominio(Long id, UnidadJson unidad) {
        if (unidad == null || unidad.id() == null || unidad.numeroInventario() == null
                || unidad.estado() == null || unidad.tipo() == null
                || unidad.tipo().duracionMaximaDias() == null || unidad.tipo().requiereAutorizacion() == null) {
            log.warn("Inventario respondió la unidad {} sin los campos del contrato", id);
            throw new DependenciaNoDisponibleException(MENSAJE_NO_DISPONIBLE);
        }
        return new UnidadInventario(
                unidad.id(),
                unidad.numeroInventario(),
                unidad.estado(),
                unidad.tipo().duracionMaximaDias(),
                unidad.tipo().requiereAutorizacion());
    }
}
