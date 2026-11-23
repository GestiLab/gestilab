package com.tedesoft.inventarioservice.delivery.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;

/**
 * Registro de intentos rechazados (SCRUM-20, CA-7 y CA-12): una línea por cada 401 o 403 con fecha, sujeto,
 * método, ruta, rol y motivo. <b>Nunca</b> incluye el token ni el contenido de la petición.
 *
 * <p>Usa un registro con nombre propio para poder filtrarlo o enviarlo aparte sin tocar el código.
 */
public final class RegistroAccesoRechazado {

    public static final String NOMBRE_REGISTRO = "gestilab.seguridad";

    private static final Logger registro = LoggerFactory.getLogger(NOMBRE_REGISTRO);

    private RegistroAccesoRechazado() {
    }

    /**
     * @param autenticacion quien hizo la petición si el servicio ya lo identificó (403); {@code null} cuando
     *                      no hay token válido (401): un token que no se pudo validar no se toma como dato.
     */
    public static void registrar(HttpServletRequest peticion, int estado, String motivo,
                                 Authentication autenticacion) {
        registro.warn("Acceso rechazado: fecha={}, estado={}, sujeto={}, metodo={}, ruta={}, rol={}, motivo={}",
                Instant.now(), estado, sujeto(autenticacion), limpiar(peticion.getMethod()),
                limpiar(peticion.getRequestURI()), Roles.comoTexto(autenticacion), limpiar(motivo));
    }

    private static String sujeto(Authentication autenticacion) {
        if (autenticacion == null || autenticacion instanceof AnonymousAuthenticationToken) {
            return "-";
        }
        return limpiar(autenticacion.getName());
    }

    /** La ruta y el sujeto los controla quien llama: sin saltos de línea no puede falsificar otra línea del registro. */
    static String limpiar(String texto) {
        if (texto == null || texto.isBlank()) {
            return "-";
        }
        return texto.replaceAll("\\p{Cntrl}", "_");
    }
}
