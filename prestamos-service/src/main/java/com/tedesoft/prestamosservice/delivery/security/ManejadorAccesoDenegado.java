package com.tedesoft.prestamosservice.delivery.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * 403 por rol: el token es válido pero su rol no alcanza para esta operación. Responde con el formato común,
 * sin datos del recurso pedido, y deja una línea en el registro de intentos rechazados.
 */
public class ManejadorAccesoDenegado implements AccessDeniedHandler {

    private final RespuestaErrorSeguridad respuesta;

    public ManejadorAccesoDenegado(RespuestaErrorSeguridad respuesta) {
        this.respuesta = respuesta;
    }

    @Override
    public void handle(HttpServletRequest peticion, HttpServletResponse respuestaHttp,
                       AccessDeniedException excepcion) throws IOException {
        RegistroAccesoRechazado.registrar(peticion, HttpStatus.FORBIDDEN.value(),
                "rol insuficiente para esta operación", SecurityContextHolder.getContext().getAuthentication());
        respuesta.escribir(respuestaHttp, HttpStatus.FORBIDDEN, RespuestaErrorSeguridad.MENSAJE_403);
    }
}
