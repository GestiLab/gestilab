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
        this.numeroInventario = numeroInventario.strip();
        this.estado = estado;
        this.observaciones = observaciones == null ? null : observaciones.strip();
    }
}
