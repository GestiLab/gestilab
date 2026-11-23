package com.tedesoft.prestamosservice.infrastructure.inventario;

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
 * petición HTTP. Si algún día se hiciera en otro hilo, habría que llevar el contexto con ella; sin token en el
 * contexto no se agrega ninguna cabecera, y inventario responde 401.
 *
 * <p>El token solo viaja al servidor de inventario configurado, porque la URL base del {@code RestClient} es
 * fija. Nunca se registra.
 */
public class ReenvioDeTokenInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest peticion, byte[] cuerpo, ClientHttpRequestExecution ejecucion)
            throws IOException {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion instanceof JwtAuthenticationToken jwt) {
            peticion.getHeaders().setBearerAuth(jwt.getToken().getTokenValue());
        }
        return ejecucion.execute(peticion, cuerpo);
    }
}
