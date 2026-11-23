package com.tedesoft.prestamosservice.infrastructure.inventario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;

/**
 * Reenvía a inventario el token de la persona que está solicitando (SCRUM-24, CA-3), para que esa consulta
 * también pase la validación de seguridad de inventario y quede a nombre de quien la pidió.
 *
 * <p>Lee el token del contexto de seguridad del hilo actual: hoy la solicitud corre síncrona en el hilo de la
 * petición HTTP. Si algún día se hiciera en otro hilo, habría que llevar el contexto con ella. Sin token en el
 * contexto no se agrega ninguna cabecera: inventario responde 401, préstamos responde 502 y aquí queda un aviso
 * que explica por qué.
 *
 * <p>El token solo viaja al servidor de inventario configurado, porque la URL base del {@code RestClient} es
 * fija. No se registra: ni aquí ni en el cliente HTTP del JDK, que escribe las cabeceras en DEBUG y por eso
 * queda en INFO en {@code application.properties}.
 */
public class ReenvioDeTokenInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ReenvioDeTokenInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest peticion, byte[] cuerpo, ClientHttpRequestExecution ejecucion)
            throws IOException {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion instanceof JwtAuthenticationToken jwt) {
            peticion.getHeaders().setBearerAuth(jwt.getToken().getTokenValue());
        } else {
            // Solo el tipo: ni el token ni las credenciales de otra autenticación
            log.warn("Consulta a inventario sin token: en el contexto de seguridad hay {} y no un JWT; inventario"
                            + " responderá 401",
                    autenticacion == null ? "ninguna autenticación" : autenticacion.getClass().getSimpleName());
        }
        return ejecucion.execute(peticion, cuerpo);
    }
}
