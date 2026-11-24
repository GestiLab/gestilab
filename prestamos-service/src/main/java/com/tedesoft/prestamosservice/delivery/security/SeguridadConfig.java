package com.tedesoft.prestamosservice.delivery.security;

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
 * Seguridad de {@code prestamos-service} (SCRUM-20, RQ-03). Misma política que {@code inventario-service}: el
 * servicio valida el token firmado por Keycloak y lee el rol que trae, sin confiar en lo que haga la interfaz.
 *
 * <ul>
 *   <li>Sin token, o con uno alterado, vencido, sin firma o de otro emisor: <b>401</b>.</li>
 *   <li>Con un token válido cuyo rol no alcanza: <b>403</b>.</li>
 *   <li>Ambos con el cuerpo de error común y una línea en el registro de intentos rechazados.</li>
 * </ul>
 *
 * <p>Esta cadena decide por <b>rol</b>. Lo que depende de <b>quién</b> es la persona (ver solo sus préstamos) lo
 * decide {@code PrestamoRestController}, porque necesita el préstamo; allí un usuario que pide algo ajeno recibe
 * el mismo 403. <b>Un endpoint nuevo declara su rol aquí desde el principio</b>; mientras no lo haga, se
 * deniega (403) para todos.
 */
@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http,
                                                 PuntoEntradaAutenticacion puntoEntrada,
                                                 ManejadorAccesoDenegado accesoDenegado,
                                                 @Value("${api.version}") String versionApi) throws Exception {
        String prestamos = "/api/" + versionApi + "/prestamos-service/prestamos";

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
                        // Retrasos (RF-08): solo el auxiliar. Va antes de GET /{id}: la primera regla que coincide gana.
                        .requestMatchers(antMatcher(HttpMethod.GET, prestamos + "/retrasos"))
                        .hasRole(Roles.AUXILIAR)
                        // Entregar, recibir y ver las fotos de la unidad: solo el auxiliar
                        .requestMatchers(
                                antMatcher(HttpMethod.PATCH, prestamos + "/*/entregar"),
                                antMatcher(HttpMethod.PATCH, prestamos + "/*/recibir"),
                                antMatcher(HttpMethod.GET, prestamos + "/*/foto-entrega"),
                                antMatcher(HttpMethod.GET, prestamos + "/*/foto-devolucion"))
                        .hasRole(Roles.AUXILIAR)
                        // Solicitar una unidad: solo el usuario (la matriz del enunciado la niega al auxiliar)
                        .requestMatchers(antMatcher(HttpMethod.POST, prestamos))
                        .hasRole(Roles.USUARIO)
                        // Listar y consultar un préstamo: los dos roles. Que el usuario solo vea lo suyo depende del sub
                        // y del préstamo, y lo decide el controlador (GET /{id} y el listado por persona, SCRUM-24).
                        .requestMatchers(
                                antMatcher(HttpMethod.GET, prestamos),
                                antMatcher(HttpMethod.GET, prestamos + "/*"))
                        .hasAnyRole(Roles.USUARIO, Roles.AUXILIAR)
                        // Lo que no tiene regla arriba se deniega: una ruta o un método sin declarar no queda abierto a
                        // ningún rol (un usuario autenticado recibe 403; sin token, 401).
                        .anyRequest().denyAll())
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
        // Si Keycloak no entrega las claves, el token se rechaza con 401 (ver DecodificadorJwtTolerante)
        return new DecodificadorJwtTolerante(decodificador);
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
