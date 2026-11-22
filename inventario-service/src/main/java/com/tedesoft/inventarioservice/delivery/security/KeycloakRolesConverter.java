package com.tedesoft.inventarioservice.delivery.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Lee los roles de realm que Keycloak pone en el access token, en {@code realm_access.roles}
 * (ver {@code keycloak/README.md}), y los convierte en autoridades {@code ROLE_<rol>}.
 *
 * <p>Es la única fuente de roles: no se leen los scopes ni {@code resource_access}. Un token sin esa ruta,
 * o con una forma inesperada, queda sin roles y por tanto sin acceso a nada (403), nunca con acceso por defecto.
 */
public class KeycloakRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    static final String RECLAMO_ACCESO = "realm_access";
    static final String RECLAMO_ROLES = "roles";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Object acceso = jwt.getClaim(RECLAMO_ACCESO);
        if (!(acceso instanceof Map<?, ?> mapa)) {
            return List.of();
        }
        Object roles = mapa.get(RECLAMO_ROLES);
        if (!(roles instanceof Collection<?> lista)) {
            return List.of();
        }
        return lista.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority(Roles.PREFIJO + rol))
                .toList();
    }
}
