package com.tedesoft.prestamosservice.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Préstamo de una unidad a una persona (RF-04).
 *
 * <p>{@code unidadId} y {@code tipoId} son referencias lógicas: la base de inventario es de otro
 * servicio y no hay clave foránea entre bases. La duración aplicada se guarda en el préstamo para que
 * un cambio posterior de la regla del tipo no reescriba el historial (RF-01 + RF-04).
 *
 * <p>El dominio no consulta el reloj del sistema: la fecha de hoy llega por parámetro (RQ-02).
 */
@Entity
@Table(name = "prestamo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prestamo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unidad_id", nullable = false)
    private Long unidadId;

    @Column(name = "tipo_id", nullable = false)
    private Long tipoId;

    /** Identificador de la persona (el {@code sub} del token cuando llegue la seguridad). */
    @Column(name = "solicitante_id", nullable = false, length = 100)
    private String solicitanteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoPrestamo estado;

    @Column(name = "duracion_aplicada_dias", nullable = false)
    private int duracionAplicadaDias;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDate fechaSolicitud;

    @Column(name = "fecha_prevista", nullable = false)
    private LocalDate fechaPrevista;

    private Prestamo(Long unidadId, Long tipoId, String solicitanteId, int duracionDias, LocalDate hoy) {
        this.unidadId = Objects.requireNonNull(unidadId, "La unidad es obligatoria");
        this.tipoId = Objects.requireNonNull(tipoId, "El tipo de equipo es obligatorio");
        if (solicitanteId == null || solicitanteId.isBlank()) {
            throw new IllegalArgumentException("El solicitante es obligatorio");
        }
        if (duracionDias <= 0) {
            throw new IllegalArgumentException("La duración aplicada debe ser mayor que cero");
        }
        Objects.requireNonNull(hoy, "La fecha de hoy es obligatoria");
        this.solicitanteId = solicitanteId.strip();
        this.estado = EstadoPrestamo.SOLICITADO;
        this.duracionAplicadaDias = duracionDias;
        this.fechaSolicitud = hoy;
        this.fechaPrevista = hoy.plusDays(duracionDias);
    }

    /**
     * Crea un préstamo recién solicitado.
     *
     * @param duracionDias duración máxima del tipo, tal como la informó inventario.
     * @param hoy          fecha de la solicitud; la provee quien llama, no el reloj del sistema.
     */
    public static Prestamo solicitar(Long unidadId, Long tipoId, String solicitanteId, int duracionDias,
                                     LocalDate hoy) {
        return new Prestamo(unidadId, tipoId, solicitanteId, duracionDias, hoy);
    }
}
