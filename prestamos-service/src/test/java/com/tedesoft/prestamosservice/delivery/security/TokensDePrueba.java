package com.tedesoft.prestamosservice.delivery.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Tokens de prueba con firma RS256 real (no simulados): así las pruebas ejercitan la misma validación de firma,
 * emisor y vigencia que usa el servicio con los tokens de Keycloak. Hay dos pares de claves: la "buena", que el
 * decodificador de prueba conoce, y una ajena, para probar tokens firmados por otro.
 */
public final class TokensDePrueba {

    public static final String EMISOR = "http://localhost:8180/realms/gestilab";
    public static final String SUJETO_USUARIO = "ana";
    public static final String SUJETO_AUXILIAR = "carlos";

    private static final RSAKey CLAVE = generar("clave-de-prueba");
    private static final RSAKey CLAVE_AJENA = generar("clave-ajena");

    private TokensDePrueba() {
    }

    /** Decodificador que valida con la clave de prueba y con las mismas reglas de emisor y vigencia del servicio. */
    public static JwtDecoder decodificador() {
        try {
            NimbusJwtDecoder decodificador = NimbusJwtDecoder.withPublicKey(CLAVE.toRSAPublicKey()).build();
            decodificador.setJwtValidator(SeguridadConfig.validadores(EMISOR));
            return decodificador;
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String auxiliar() {
        return valido(SUJETO_AUXILIAR, "auxiliar");
    }

    public static String usuario() {
        return valido(SUJETO_USUARIO, "usuario");
    }

    /** Token válido y firmado, con el sujeto y los roles indicados (puede no traer ninguno). */
    public static String valido(String sujeto, String... roles) {
        return firmar(CLAVE, EMISOR, sujeto, Instant.now().plusSeconds(3600), roles);
    }

    /** Firmado y bien formado, pero vencido hace una hora (el margen de tolerancia del validador es de 60 s). */
    public static String vencido() {
        return firmar(CLAVE, EMISOR, SUJETO_AUXILIAR, Instant.now().minusSeconds(3600), "auxiliar");
    }

    /** Firma correcta pero emitido por otro realm. */
    public static String deOtroEmisor() {
        return firmar(CLAVE, "http://otro-keycloak:8180/realms/ajeno", SUJETO_AUXILIAR,
                Instant.now().plusSeconds(3600), "auxiliar");
    }

    /** Emisor correcto pero firmado con una clave que el servicio no conoce. */
    public static String firmadoConOtraClave() {
        return firmar(CLAVE_AJENA, EMISOR, SUJETO_AUXILIAR, Instant.now().plusSeconds(3600), "auxiliar");
    }

    /** Sin firma (alg=none): lo que enviaría quien intenta fabricar un token a mano. */
    public static String sinFirma() {
        JWTClaimsSet claims = claims(EMISOR, SUJETO_AUXILIAR, Instant.now().plusSeconds(3600), "auxiliar");
        return new PlainJWT(claims).serialize();
    }

    /**
     * Token de un usuario al que se le cambió el rol a auxiliar dentro del contenido, conservando la firma
     * original: la firma ya no corresponde y debe rechazarse.
     */
    public static String alterado() {
        String[] partes = usuario().split("\\.");
        String contenido = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8)
                .replace("\"usuario\"", "\"auxiliar\"");
        String nuevo = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(contenido.getBytes(StandardCharsets.UTF_8));
        return partes[0] + "." + nuevo + "." + partes[2];
    }

    private static String firmar(RSAKey clave, String emisor, String sujeto, Instant vence, String... roles) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(clave.getKeyID()).build(),
                    claims(emisor, sujeto, vence, roles));
            jwt.sign(new RSASSASigner(clave));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static JWTClaimsSet claims(String emisor, String sujeto, Instant vence, String... roles) {
        return new JWTClaimsSet.Builder()
                .issuer(emisor)
                .subject(sujeto)
                .issueTime(Date.from(vence.minusSeconds(7200)))
                .expirationTime(Date.from(vence))
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .build();
    }

    private static RSAKey generar(String id) {
        try {
            return new RSAKeyGenerator(2048).keyID(id).generate();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
