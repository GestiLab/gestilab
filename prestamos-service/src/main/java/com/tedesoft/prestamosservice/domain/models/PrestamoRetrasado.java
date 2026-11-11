package com.tedesoft.prestamosservice.domain.models;

import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Préstamo entregado cuya fecha de devolución ya pasó. Es una vista calculada al consultar: el retraso
 * no se guarda en la base (D-04), por eso no existe como estado ni como columna.
 */
public record PrestamoRetrasado(
        Long prestamoId,
        Long unidadId,
        String solicitanteId,
        LocalDate fechaDevolucionPrevista,
        long diasRetraso
) {

    /**
     * La regla del retraso (RF-08): está en manos de la persona y su fecha prevista ya pasó. El día del
     * vencimiento todavía no cuenta. Vive en el dominio y no en la consulta para probarla sin base de datos (RQ-02).
     *
     * @param hoy fecha de hoy; la provee quien llama, no el reloj del sistema.
     */
    public static boolean aplicaA(Prestamo prestamo, LocalDate hoy) {
        return prestamo.getEstado() == EstadoPrestamo.ENTREGADO && prestamo.getFechaPrevista().isBefore(hoy);
    }

    public static PrestamoRetrasado desde(Prestamo prestamo, LocalDate hoy) {
        return new PrestamoRetrasado(prestamo.getId(), prestamo.getUnidadId(), prestamo.getSolicitanteId(),
                prestamo.getFechaPrevista(), ChronoUnit.DAYS.between(prestamo.getFechaPrevista(), hoy));
    }
}
