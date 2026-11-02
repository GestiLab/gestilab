package com.tedesoft.inventario.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Tipo de equipo (p. ej. "Multímetro"). Un tipo NO es una unidad física: las reglas de préstamo
 * pertenecen al tipo y se guardan como datos (RQ-01 Modificabilidad).
 *
 * <p>Agregar un tipo nuevo es insertar una fila; ninguna clase de lógica cambia. El dominio no
 * consulta el reloj del sistema: la fecha llega siempre por parámetro (RQ-02 Testabilidad).
 */
@Entity
@Table(name = "tipo_equipo")
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TipoEquipo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Column(name = "duracion_maxima_dias", nullable = false)
    private int duracionMaximaDias;

    @Column(name = "requiere_autorizacion_previa", nullable = false)
    private boolean requiereAutorizacionPrevia;

    private TipoEquipo(String nombre, String descripcion, int duracionMaximaDias, boolean requiereAutorizacionPrevia) {
        aplicar(nombre, descripcion, duracionMaximaDias, requiereAutorizacionPrevia);
    }

    public static TipoEquipo crear(String nombre, String descripcion, int duracionMaximaDias,
                                   boolean requiereAutorizacionPrevia) {
        return new TipoEquipo(nombre, descripcion, duracionMaximaDias, requiereAutorizacionPrevia);
    }

    /** Reemplaza los datos y reglas del tipo. Si algo es inválido, el estado actual no cambia. */
    public void actualizar(String nombre, String descripcion, int duracionMaximaDias,
                           boolean requiereAutorizacionPrevia) {
        aplicar(nombre, descripcion, duracionMaximaDias, requiereAutorizacionPrevia);
    }

    /**
     * Fecha de devolución propuesta según la regla de este tipo (RF-01).
     *
     * @param fechaInicio fecha desde la que corre el préstamo; la provee quien llama, no el reloj.
     */
    public LocalDate calcularFechaDevolucion(LocalDate fechaInicio) {
        Objects.requireNonNull(fechaInicio, "La fecha de inicio es obligatoria");
        return fechaInicio.plusDays(duracionMaximaDias);
    }

    private void aplicar(String nombre, String descripcion, int duracionMaximaDias,
                         boolean requiereAutorizacionPrevia) {
        // Se valida todo antes de asignar para no dejar el objeto a medias.
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaPrestamoInvalidaException("nombre", "El nombre del tipo de equipo es obligatorio");
        }
        if (duracionMaximaDias <= 0) {
            throw new ReglaPrestamoInvalidaException("duracionMaximaDias",
                    "La duración máxima debe ser mayor que cero");
        }
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.duracionMaximaDias = duracionMaximaDias;
        this.requiereAutorizacionPrevia = requiereAutorizacionPrevia;
    }
}
