package com.tedesoft.inventarioservice.delivery.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

/**
 * Seguridad de {@code inventario-service} (SCRUM-20, RQ-03): la autorización se comprueba aquí, en el servicio,
 * y no en la interfaz. Keycloak emite un token firmado; el servicio lo valida y lee el rol que trae dentro.
 *
 * <ul>
 *   <li>Sin token, o con un token alterado, vencido, sin firma o de otro emisor: <b>401</b>.</li>
 *   <li>Con un token válido de un rol que no alcanza: <b>403</b>.</li>
 *   <li>Ambos con el cuerpo de error común y una línea en el registro de intentos rechazados.</li>
 * </ul>
 *
 * <p>Los roles se aplican por ruta y método en este único lugar, antes de llegar al controlador. Así un
 * {@code usuario} recibe 403 aunque su petición traiga un cuerpo inválido: nunca ve un 400 que le describa el
 * recurso. <b>Un endpoint nuevo declara su rol aquí desde el principio</b>; mientras no lo haga, solo lo alcanza
 * quien tenga alguno de los dos roles.
 */
@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    private static final String BASE = "/api/v1/inventario-service";

    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http,
                                                 PuntoEntradaAutenticacion puntoEntrada,
                                                 ManejadorAccesoDenegado accesoDenegado) throws Exception {
        http
                // API con token en el encabezado y sin cookies de sesión: no hay nada que falsificar entre sitios
                .csrf(AbstractHttpConfigurer::disable)
                // CORS con los valores por defecto: sin reglas propias; SCRUM-29 las define sin reescribir la cadena
                .cors(Customizer.withDefaults())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reglas -> reglas
                        // Redirección interna de Spring a /error tras un fallo: sin esto un 400 o un 404 se vería como 401.
                        // Pedir /error directamente sigue exigiendo token.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // /salud la usan el CI y las sondas del despliegue, que no llevan token
                        .requestMatchers(antMatcher("/salud")).permitAll()
                        // Consultar el catálogo: los dos roles
                        .requestMatchers(
                                antMatcher(HttpMethod.GET, BASE + "/tipos-equipo"),
                                antMatcher(HttpMethod.GET, BASE + "/tipos-equipo/*"),
                                antMatcher(HttpMethod.GET, BASE + "/unidades"),
                                antMatcher(HttpMethod.GET, BASE + "/unidades/*"))
                        .hasAnyRole(Roles.USUARIO, Roles.AUXILIAR)
                        // Dar de alta y editar tipos (con sus reglas) y unidades: solo el auxiliar
                        .requestMatchers(
                                antMatcher(HttpMethod.POST, BASE + "/tipos-equipo"),
                                antMatcher(HttpMethod.PUT, BASE + "/tipos-equipo/*"),
                                antMatcher(HttpMethod.POST, BASE + "/unidades"),
                                antMatcher(HttpMethod.PUT, BASE + "/unidades/*"))
                        .hasRole(Roles.AUXILIAR)
                        .anyRequest().hasAnyRole(Roles.USUARIO, Roles.AUXILIAR))
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(puntoEntrada)
                        .accessDeniedHandler(accesoDenegado))
                .oauth2ResourceServer(recurso -> recurso
                        .authenticationEntryPoint(puntoEntrada)
                        .accessDeniedHandler(accesoDenegado)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(convertidorDeJwt())));
        return http.build();
    }

    @Bean
    public RespuestaErrorSeguridad respuestaErrorSeguridad(ObjectMapper objectMapper) {
        return new RespuestaErrorSeguridad(objectMapper);
    }

    @Bean
    public PuntoEntradaAutenticacion puntoEntradaAutenticacion(RespuestaErrorSeguridad respuesta) {
        return new PuntoEntradaAutenticacion(respuesta);
    }

    @Bean
    public ManejadorAccesoDenegado manejadorAccesoDenegado(RespuestaErrorSeguridad respuesta) {
        return new ManejadorAccesoDenegado(respuesta);
    }

    /**
     * Valida la firma contra las claves públicas de Keycloak (RS256) y el emisor y la vigencia del token.
     * La descarga de las claves es perezosa y con tiempo máximo: sin Keycloak el servicio arranca, y si se
     * cae, las peticiones con token responden 401 en lugar de quedarse colgadas.
     */
    @Bean
    public JwtDecoder decodificadorJwt(@Value("${gestilab.seguridad.jwk-set-uri}") String jwkSetUri,
                                       @Value("${gestilab.seguridad.issuer}") String emisor) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(3));
        fabrica.setReadTimeout(Duration.ofSeconds(3));
        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .restOperations(new RestTemplate(fabrica))
                .build();
        decodificador.setJwtValidator(validadores(emisor));
        return decodificador;
    }

    /** Vigencia ({@code exp}, {@code nbf}) y emisor ({@code iss}). Las pruebas arman su decodificador con esto mismo. */
    public static OAuth2TokenValidator<Jwt> validadores(String emisor) {
        return JwtValidators.createDefaultWithIssuer(emisor);
    }

    private static JwtAuthenticationConverter convertidorDeJwt() {
        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(new KeycloakRolesConverter());
        return convertidor;
    }
}
