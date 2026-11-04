package com.tedesoft.inventarioservice.domain.entities;

import com.tedesoft.inventarioservice.domain.exception.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Tipo de equipo (p. ej. "Multímetro"). Un tipo NO es una unidad física: las reglas de préstamo
 * pertenecen al tipo y se guardan como datos (RQ-01 Modificabilidad, decisión D-01).
 *
 * <p>Agregar un tipo nuevo es insertar una fila; ninguna clase de lógica cambia. El dominio no
 * consulta el reloj del sistema: la fecha llega siempre por parámetro (RQ-02 Testabilidad).
 */
@Entity
@Table(name = "tipo_equipo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TipoEquipo {

    /** Tope razonable: evita fechas de devolución fuera del rango de LocalDate. */
    public static final int DURACION_MAXIMA_PERMITIDA_DIAS = 365;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Column(name = "duracion_maxima_dias", nullable = false)
    private int duracionMaximaDias;

    @Column(name = "requiere_autorizacion", nullable = false)
    private boolean requiereAutorizacion;

    private TipoEquipo(String nombre, String descripcion, int duracionMaximaDias, boolean requiereAutorizacion) {
        aplicar(nombre, descripcion, duracionMaximaDias, requiereAutorizacion);
    }

    public static TipoEquipo crear(String nombre, String descripcion, int duracionMaximaDias,
                                   boolean requiereAutorizacion) {
        return new TipoEquipo(nombre, descripcion, duracionMaximaDias, requiereAutorizacion);
    }

    /** Reemplaza los datos y reglas del tipo. Si algo es inválido, el estado actual no cambia. */
    public void actualizar(String nombre, String descripcion, int duracionMaximaDias,
                           boolean requiereAutorizacion) {
        aplicar(nombre, descripcion, duracionMaximaDias, requiereAutorizacion);
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
                         boolean requiereAutorizacion) {
        // Se valida todo antes de asignar para no dejar el objeto a medias.
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("El nombre del tipo de equipo es obligatorio");
        }
        if (duracionMaximaDias <= 0) {
            throw new ReglaNegocioException("La duración máxima debe ser mayor que cero");
        }
        if (duracionMaximaDias > DURACION_MAXIMA_PERMITIDA_DIAS) {
            throw new ReglaNegocioException(
                    "La duración máxima no puede superar " + DURACION_MAXIMA_PERMITIDA_DIAS + " días");
        }
        this.nombre = nombre.strip();
        this.descripcion = descripcion == null ? null : descripcion.strip();
        this.duracionMaximaDias = duracionMaximaDias;
        this.requiereAutorizacion = requiereAutorizacion;
    }
}
