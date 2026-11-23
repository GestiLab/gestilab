package com.tedesoft.prestamosservice.delivery.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * 401: no hay token, o el que llegó no se pudo validar (alterado, vencido, sin firma o firmado por quien no es
 * Keycloak). Responde con el formato común y deja una línea en el registro de intentos rechazados.
 */
public class PuntoEntradaAutenticacion implements AuthenticationEntryPoint {

    private final RespuestaErrorSeguridad respuesta;

    public PuntoEntradaAutenticacion(RespuestaErrorSeguridad respuesta) {
        this.respuesta = respuesta;
    }

    @Override
    public void commence(HttpServletRequest peticion, HttpServletResponse respuestaHttp,
                         AuthenticationException excepcion) throws IOException {
        RegistroAccesoRechazado.registrar(peticion, HttpStatus.UNAUTHORIZED.value(), motivo(peticion, excepcion), null);
        respuestaHttp.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        respuesta.escribir(respuestaHttp, HttpStatus.UNAUTHORIZED, RespuestaErrorSeguridad.MENSAJE_401);
    }

    /** Solo para el registro: la respuesta a quien llama es siempre la misma. */
    static String motivo(HttpServletRequest peticion, AuthenticationException excepcion) {
        String cabecera = peticion.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecera == null || cabecera.isBlank()) {
            return "token ausente";
        }
        if (!cabecera.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return "se esperaba un token Bearer";
        }
        if (excepcion instanceof AuthenticationServiceException
                || excepcion.getCause() instanceof ClavesNoDisponiblesException) {
            return "no se pudo validar el token (servicio de claves no disponible)";
        }
        return "token inválido, vencido o de otro emisor";
    }
}
