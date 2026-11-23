package com.tedesoft.inventarioservice.delivery.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.stream.Collectors;

/**
 * Los dos roles del realm de Keycloak ({@code keycloak/gestilab-realm.json}). Dentro de Spring cada uno viaja
 * como la autoridad {@code ROLE_<nombre>}, que es lo que entienden {@code hasRole} y {@code hasAnyRole}.
 */
public final class Roles {

    public static final String USUARIO = "usuario";
    public static final String AUXILIAR = "auxiliar";

    static final String PREFIJO = "ROLE_";

    private Roles() {
    }

    public static boolean esAuxiliar(Authentication autenticacion) {
        return tiene(autenticacion, AUXILIAR);
    }

    public static boolean tiene(Authentication autenticacion, String rol) {
        return autenticacion != null && autenticacion.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch((PREFIJO + rol)::equals);
    }

    /** Roles de la autenticación, sin el prefijo y separados por coma; "-" si no hay ninguna persona identificada. */
    public static String comoTexto(Authentication autenticacion) {
        if (autenticacion == null || autenticacion instanceof AnonymousAuthenticationToken) {
            return "-";
        }
        String roles = autenticacion.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(autoridad -> autoridad.startsWith(PREFIJO))
                .map(autoridad -> autoridad.substring(PREFIJO.length()))
                .collect(Collectors.joining(","));
        return roles.isEmpty() ? "-" : roles;
    }
}
