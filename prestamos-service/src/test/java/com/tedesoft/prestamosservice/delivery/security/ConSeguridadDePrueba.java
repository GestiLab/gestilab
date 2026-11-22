package com.tedesoft.prestamosservice.delivery.security;

import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Para las pruebas HTTP existentes: activa la seguridad real del servicio ({@link SeguridadConfig}), valida con
 * tokens firmados de prueba y envía por defecto el de un auxiliar (SCRUM-20, CA-14).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import({SeguridadConfig.class, DecodificadorDePruebaConfig.class, TokenPorDefectoConfig.class})
public @interface ConSeguridadDePrueba {
}
