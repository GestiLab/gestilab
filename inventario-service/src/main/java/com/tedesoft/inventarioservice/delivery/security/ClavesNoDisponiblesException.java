package com.tedesoft.inventarioservice.delivery.security;

import org.springframework.security.oauth2.jwt.BadJwtException;

/**
 * No se pudo comprobar la firma del token porque Keycloak no entregó sus claves públicas (caído, sin respuesta
 * a tiempo o con un error). Es una {@link BadJwtException} a propósito: Spring la convierte en un 401 y no la
 * relanza como un error del servidor (500).
 */
public class ClavesNoDisponiblesException extends BadJwtException {

    public ClavesNoDisponiblesException(Throwable causa) {
        super("servicio de claves no disponible", causa);
    }
}
