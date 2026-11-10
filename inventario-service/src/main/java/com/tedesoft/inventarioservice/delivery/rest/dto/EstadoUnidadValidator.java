package com.tedesoft.inventarioservice.delivery.rest.dto;

import com.tedesoft.inventarioservice.domain.entities.EstadoUnidad;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** La lista de estados sale del enum: si se agrega uno, la validación y el mensaje lo recogen solos. */
public class EstadoUnidadValidator implements ConstraintValidator<EstadoUnidadValido, String> {

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext contexto) {
        if (valor == null || EstadoUnidad.esValido(valor)) {
            return true;
        }
        contexto.disableDefaultConstraintViolation();
        contexto.buildConstraintViolationWithTemplate(
                        "Estado no permitido. Valores válidos: " + EstadoUnidad.permitidosComoTexto())
                .addConstraintViolation();
        return false;
    }
}
