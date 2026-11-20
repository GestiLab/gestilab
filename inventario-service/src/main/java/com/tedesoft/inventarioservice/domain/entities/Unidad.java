package com.tedesoft.inventarioservice.domain.entities;

import com.tedesoft.inventarioservice.domain.exception.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Unidad física del inventario (p. ej. "multímetro MUL-014"). Pertenece siempre a un {@link TipoEquipo},
 * que es quien dicta las reglas de préstamo: la unidad no las duplica (RQ-01, decisión D-01).
 *
 * <p>La unicidad del número de inventario la garantiza la base de datos (restricción única) y la
 * verifica antes el servicio para responder un 409 claro en lugar de una excepción de base de datos.
 */
@Entity
@Table(name = "unidad",
        uniqueConstraints = @UniqueConstraint(name = "uk_unidad_numero_inventario", columnNames = "numero_inventario"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Unidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_inventario", nullable = false, length = 50)
    private String numeroInventario;

    /** Se guarda por nombre (no por ordinal) para que reordenar el enum no corrompa los datos. */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoUnidad estado;

    @Column(name = "observaciones", length = 500)
    private String observaciones;

    @Column(name = "ultimo_evento_en")
    private Instant ultimoEventoEn;

    /** EAGER: la respuesta de la unidad siempre incluye las reglas de su tipo (contrato openapi.yaml). */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tipo_equipo_id", nullable = false)
    private TipoEquipo tipo;

    private Unidad(TipoEquipo tipo, String numeroInventario, EstadoUnidad estado, String observaciones) {
        aplicar(tipo, numeroInventario, estado, observaciones);
    }

    public static Unidad crear(TipoEquipo tipo, String numeroInventario, EstadoUnidad estado,
                               String observaciones) {
        return new Unidad(tipo, numeroInventario, estado, observaciones);
    }

    /** Reemplaza los datos de la unidad. Si algo es inválido, el estado actual no cambia. */
    public void actualizar(TipoEquipo tipo, String numeroInventario, EstadoUnidad estado,
                           String observaciones) {
        aplicar(tipo, numeroInventario, estado, observaciones);
    }

    public boolean sePuedePrestar() {
        return estado.sePuedePrestar();
    }

    /** Aplica un evento de préstamo solo si es válido y no precede al último evento aplicado. */
    public ResultadoEventoEstado aplicarEventoDeEstado(EstadoUnidad nuevoEstado, Instant fechaEvento) {
        Objects.requireNonNull(nuevoEstado, "El estado del evento es obligatorio");
        Objects.requireNonNull(fechaEvento, "La fecha del evento es obligatoria");

        if (nuevoEstado != EstadoUnidad.PRESTADA && nuevoEstado != EstadoUnidad.DISPONIBLE) {
            return ResultadoEventoEstado.ESTADO_NO_ADMITIDO;
        }
        if (estado != EstadoUnidad.PRESTADA && estado != EstadoUnidad.DISPONIBLE) {
            return ResultadoEventoEstado.NO_APLICABLE;
        }

        if (ultimoEventoEn != null) {
            int orden = fechaEvento.compareTo(ultimoEventoEn);
            if (orden < 0 || (orden == 0 && nuevoEstado != estado)) {
                return ResultadoEventoEstado.FUERA_DE_ORDEN;
            }
            if (orden == 0) {
                return ResultadoEventoEstado.SIN_CAMBIO;
            }
        }

        ultimoEventoEn = fechaEvento;
        if (nuevoEstado == estado) {
            return ResultadoEventoEstado.SIN_CAMBIO;
        }

        estado = nuevoEstado;
        return ResultadoEventoEstado.APLICADO;
    }

    private void aplicar(TipoEquipo tipo, String numeroInventario, EstadoUnidad estado,
                         String observaciones) {
        // Se valida todo antes de asignar para no dejar el objeto a medias.
        if (tipo == null) {
            throw new ReglaNegocioException("La unidad debe pertenecer a un tipo de equipo");
        }
        if (numeroInventario == null || numeroInventario.isBlank()) {
            throw new ReglaNegocioException("El número de inventario es obligatorio");
        }
        if (estado == null) {
            throw new ReglaNegocioException(
                    "El estado es obligatorio. Valores válidos: " + EstadoUnidad.permitidosComoTexto());
        }
        this.tipo = tipo;
        // En mayúsculas: "mul-014" y "MUL-014" son la misma unidad, también para la restricción única de la
        // base de datos, que distingue mayúsculas (dos solicitudes simultáneas no pueden crear ambas).
        this.numeroInventario = numeroInventario.strip().toUpperCase(Locale.ROOT);
        this.estado = estado;
        this.observaciones = observaciones == null ? null : observaciones.strip();
    }
}
