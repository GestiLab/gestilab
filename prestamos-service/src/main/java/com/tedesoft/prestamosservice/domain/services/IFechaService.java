package com.tedesoft.prestamosservice.domain.services;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public interface IFechaService {

    /** Fecha de hoy en la zona del reloj inyectado. */
    LocalDate hoy();

    /** Instante actual con la zona del reloj inyectado (fecha de los eventos). */
    OffsetDateTime ahora();
}
