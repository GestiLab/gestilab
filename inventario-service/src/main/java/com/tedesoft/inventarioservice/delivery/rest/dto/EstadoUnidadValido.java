package com.tedesoft.inventarioservice.delivery.rest.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * El texto debe ser un estado de unidad permitido. Un valor nulo se considera válido: de la
 * obligatoriedad se encarga {@code @NotNull}. El mensaje de error lista los estados permitidos.
 */
@Documented
@Constraint(validatedBy = EstadoUnidadValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR,
        ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface EstadoUnidadValido {

    String message() default "Estado no permitido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
