package com.tedesoft.inventarioservice.delivery.rest;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lee {@code docs/contrato/openapi.yaml} (SCRUM-5) para las pruebas que comparan una respuesta con el contrato.
 * Las pruebas deben correr desde la carpeta {@code inventario-service} dentro del repositorio.
 */
final class ContratoOpenApi {

    private static final Path CONTRATO = Path.of("..", "docs", "contrato", "openapi.yaml");

    private final Map<String, Object> openapi;

    private ContratoOpenApi(Map<String, Object> openapi) {
        this.openapi = openapi;
    }

    static ContratoOpenApi cargar() throws Exception {
        assertTrue(Files.exists(CONTRATO), "No se encontró " + CONTRATO.toAbsolutePath().normalize()
                + ". Las pruebas deben correr desde la carpeta inventario-service dentro del repositorio.");
        YamlMapFactoryBean yaml = new YamlMapFactoryBean();
        yaml.setResources(new FileSystemResource(CONTRATO));
        yaml.afterPropertiesSet();
        return new ContratoOpenApi(yaml.getObject());
    }

    /** Ejemplo de la respuesta 200 de {@code GET /unidades/{id}}. */
    @SuppressWarnings("unchecked")
    Map<String, Object> ejemploDeUnidad() {
        return (Map<String, Object>) en("paths", "/unidades/{id}", "get", "responses", "200", "content",
                "application/json", "example");
    }

    @SuppressWarnings("unchecked")
    List<String> estadosPermitidos() {
        return (List<String>) en("components", "schemas", "EstadoUnidad", "enum");
    }

    /** Cada campo del ejemplo existe en la respuesta con el mismo tipo JSON (los valores pueden variar). */
    void exigirMismosCamposYTipos(String ruta, JsonNode ejemplo, JsonNode real) {
        assertNotNull(real, "Falta el campo " + ruta + " en la respuesta");
        assertEquals(ejemplo.getNodeType(), real.getNodeType(), "Tipo distinto en " + ruta);
        if (ejemplo.isObject()) {
            ejemplo.fieldNames().forEachRemaining(
                    campo -> exigirMismosCamposYTipos(ruta + "." + campo, ejemplo.get(campo), real.get(campo)));
        }
    }

    /** Los campos {@code required} del esquema vienen en la respuesta. */
    @SuppressWarnings("unchecked")
    void exigirObligatorios(String esquema, JsonNode real) {
        List<String> obligatorios = (List<String>) en("components", "schemas", esquema, "required");
        for (String campo : obligatorios) {
            assertTrue(real.has(campo), "Falta el campo obligatorio '" + campo + "' del esquema " + esquema);
        }
    }

    @SuppressWarnings("unchecked")
    private Object en(String... camino) {
        Object actual = openapi;
        for (String clave : camino) {
            assertTrue(actual instanceof Map, "El contrato no tiene la ruta " + String.join(" > ", camino));
            actual = ((Map<String, Object>) actual).get(clave);
            assertNotNull(actual, "El contrato no tiene '" + clave + "' en " + String.join(" > ", camino));
        }
        return actual;
    }
}
