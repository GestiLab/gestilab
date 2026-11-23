package com.tedesoft.inventarioservice.delivery.security;

import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Envuelve al decodificador real. Un token alterado, vencido, sin firma o de otro emisor ya llega como
 * {@link BadJwtException} y sigue igual. Cualquier otro fallo (típicamente, no poder descargar las claves de
 * Keycloak) se convierte en {@link ClavesNoDisponiblesException}: sin esto, Spring Security relanza el error y la
 * persona recibe un 500 sin el formato común y sin línea en el registro de intentos rechazados.
 */
public class DecodificadorJwtTolerante implements JwtDecoder {

    private final JwtDecoder interno;

    public DecodificadorJwtTolerante(JwtDecoder interno) {
        this.interno = interno;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            return interno.decode(token);
        } catch (BadJwtException invalido) {
            throw invalido;
        } catch (JwtException noSePudoComprobar) {
            throw new ClavesNoDisponiblesException(noSePudoComprobar);
        }
    }
}
