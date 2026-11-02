package com.tedesoft.prestamosservice.domain.services;

import java.time.LocalDate;

public interface IFechaService {

    /** Fecha de hoy en la zona del reloj inyectado. */
    LocalDate hoy();
}
