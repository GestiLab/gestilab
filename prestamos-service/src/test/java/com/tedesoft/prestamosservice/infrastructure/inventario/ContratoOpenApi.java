package com.tedesoft.prestamosservice.infrastructure.inventario;

import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.FileSystemResource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lee el ejemplo de {@code docs/contrato/openapi.yaml} (SCRUM-5) para comprobar que el cliente entiende lo que
 * el contrato promete. Las pruebas deben correr desde la carpeta {@code prestamos-service} dentro del repositorio.
 * (Es una copia mínima de la ayuda de inventario-service: los servicios no comparten código.)
 */
final class ContratoOpenApi {

    private static final Path CONTRATO = Path.of("..", "docs", "contrato", "openapi.yaml");

    private ContratoOpenApi() {
    }

    /** Ejemplo de la respuesta 200 de {@code GET /unidades/{id}}. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> ejemploDeUnidad() throws Exception {
        assertTrue(Files.exists(CONTRATO), "No se encontró " + CONTRATO.toAbsolutePath().normalize()
                + ". Las pruebas deben correr desde la carpeta prestamos-service dentro del repositorio.");
        YamlMapFactoryBean yaml = new YamlMapFactoryBean();
        yaml.setResources(new FileSystemResource(CONTRATO));
        yaml.afterPropertiesSet();
        Object actual = yaml.getObject();
        for (String clave : new String[]{"paths", "/unidades/{id}", "get", "responses", "200", "content",
                "application/json", "example"}) {
            assertTrue(actual instanceof Map, "El contrato no tiene la ruta hasta '" + clave + "'");
            actual = ((Map<String, Object>) actual).get(clave);
            assertNotNull(actual, "El contrato no tiene '" + clave + "'");
        }
        return (Map<String, Object>) actual;
    }
}
