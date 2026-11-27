
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

import com.tedesoft.prestamosservice.domain.exception.ReglaNegocioException;

/**
 * Préstamo de una unidad a una persona (RF-04).
 *
 * unidadId y tipoId son referencias lógicas porque
 * inventario pertenece a otro microservicio.
 *
 * La duración aplicada se conserva en el préstamo.
 * El dominio recibe la fecha de solicitud por parámetro.
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

    /** Identificador de la persona obtenido del token. */
    @Column(name = "solicitante_id", nullable = false, length = 100)
    private String solicitanteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    private EstadoPrestamo estado;

    @Column(name = "duracion_aplicada_dias", nullable = false)
    private int duracionAplicadaDias;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDate fechaSolicitud;

    @Column(name = "fecha_prevista", nullable = false)
    private LocalDate fechaPrevista;

    /**
     * Clave de la fotografía tomada al entregar la unidad.
     * La imagen se almacena en S3, no en PostgreSQL.
     */
    @Column(name = "foto_entrega_clave", length = 255)
    private String fotoEntregaClave;

    /** Clave de la fotografía tomada al recibir la unidad. */
    @Column(name = "foto_devolucion_clave", length = 255)
    private String fotoDevolucionClave;

    // ==========================================
    // CONSTRUCTOR
    // ==========================================

    private Prestamo(
            Long unidadId,
            Long tipoId,
            String solicitanteId,
            int duracionDias,
            LocalDate hoy,
            boolean requiereAutorizacion) {

        this.unidadId = Objects.requireNonNull(
                unidadId, "La unidad es obligatoria");

        this.tipoId = Objects.requireNonNull(
                tipoId, "El tipo de equipo es obligatorio");

        if (solicitanteId == null || solicitanteId.isBlank()) {
            throw new IllegalArgumentException(
                    "El solicitante es obligatorio");
        }

        if (duracionDias <= 0) {
            throw new IllegalArgumentException(
                    "La duración aplicada debe ser mayor que cero");
        }

        Objects.requireNonNull(
                hoy, "La fecha de hoy es obligatoria");

        this.solicitanteId = solicitanteId.strip();

        // CA-1 y CA-2: decidir según la regla de inventario.
        this.estado = requiereAutorizacion
                ? EstadoPrestamo.PENDIENTE_AUTORIZACION
                : EstadoPrestamo.SOLICITADO;

        this.duracionAplicadaDias = duracionDias;

        // CA-10: el plazo comienza desde la solicitud.
        this.fechaSolicitud = hoy;
        this.fechaPrevista = hoy.plusDays(duracionDias);
    }

    // ==========================================
    // SOLICITAR PRESTAMO
    // ==========================================

    /**
     * Mantiene la compatibilidad con las solicitudes
     * que no requieren autorización previa.
     */
    public static Prestamo solicitar(
            Long unidadId,
            Long tipoId,
            String solicitanteId,
            int duracionDias,
            LocalDate hoy) {

        return solicitar(
                unidadId,
                tipoId,
                solicitanteId,
                duracionDias,
                hoy,
                false);
    }

    /**
     * Crea un préstamo aplicando la regla de autorización
     * recibida desde inventario.
     */
    public static Prestamo solicitar(
            Long unidadId,
            Long tipoId,
            String solicitanteId,
            int duracionDias,
            LocalDate hoy,
            boolean requiereAutorizacion) {

        return new Prestamo(
                unidadId,
                tipoId,
                solicitanteId,
                duracionDias,
                hoy,
                requiereAutorizacion);
    }

    // ==========================================
    // AUTORIZAR PRESTAMO
    // ==========================================

    public void autorizar() {

        // CA-5: solamente se autorizan préstamos pendientes.
        if (estado != EstadoPrestamo.PENDIENTE_AUTORIZACION) {

            throw new ReglaNegocioException(
                    "No se puede autorizar el préstamo. Estado actual: "
                            + estado);
        }

        // CA-3: pendiente pasa a solicitado.
        // CA-10: no se modifican las fechas.
        this.estado = EstadoPrestamo.SOLICITADO;
    }

    // ==========================================
    // ENTREGAR PRESTAMO
    // ==========================================

    public void entregar() {

        // CA-4: no entregar sin autorización previa.
        if (estado == EstadoPrestamo.PENDIENTE_AUTORIZACION) {

            throw new ReglaNegocioException(
                    "No se puede entregar el préstamo. "
                            + "Falta la autorización previa.");
        }

        if (estado != EstadoPrestamo.SOLICITADO) {

            throw new ReglaNegocioException(
                    "No se puede entregar el préstamo. Estado actual: "
                            + estado);
        }

        this.estado = EstadoPrestamo.ENTREGADO;
    }

    // ==========================================
    // RECIBIR PRESTAMO
    // ==========================================

    public void recibir() {

        if (estado != EstadoPrestamo.ENTREGADO) {

            throw new ReglaNegocioException(
                    "No se puede recibir el préstamo. Estado actual: "
                            + estado);
        }

        this.estado = EstadoPrestamo.DEVUELTO;
    }

    // ==========================================
    // REGISTRAR FOTO DE ENTREGA
    // ==========================================

    public void registrarFotoEntrega(String clave) {

        if (clave == null || clave.isBlank()) {

            throw new IllegalArgumentException(
                    "La clave de la fotografía de entrega es obligatoria");
        }

        if (this.estado != EstadoPrestamo.ENTREGADO) {

            throw new IllegalStateException(
                    "Solo se puede registrar la fotografía de un préstamo entregado");
        }

        if (this.fotoEntregaClave != null) {

            throw new IllegalStateException(
                    "El préstamo ya tiene fotografía de entrega");
        }

        this.fotoEntregaClave = clave;
    }

    // ==========================================
    // REGISTRAR FOTO DE DEVOLUCION
    // ==========================================

    public void registrarFotoDevolucion(String clave) {

        if (clave == null || clave.isBlank()) {

            throw new IllegalArgumentException(
                    "La clave de la fotografía de devolución es obligatoria");
        }

        if (this.estado != EstadoPrestamo.DEVUELTO) {

            throw new IllegalStateException(
                    "Solo se puede registrar la fotografía de un préstamo devuelto");
        }

        if (this.fotoDevolucionClave != null) {

            throw new IllegalStateException(
                    "El préstamo ya tiene fotografía de devolución");
        }

        this.fotoDevolucionClave = clave;
    }
}
