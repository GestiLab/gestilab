
package com.tedesoft.prestamosservice.infrastructure.messaging;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "evento_unidad_pendiente")
public class EventoUnidadPendiente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long unidadId;

    @Column(nullable = false)
    private String nuevoEstado;

    @Column(nullable = false)
    private OffsetDateTime fecha;

    @Column(nullable = false)
    private boolean publicado = false;

    protected EventoUnidadPendiente() {
    }

    public EventoUnidadPendiente(
            Long unidadId,
            String nuevoEstado,
            OffsetDateTime fecha) {

        this.unidadId = unidadId;
        this.nuevoEstado = nuevoEstado;
        this.fecha = fecha;
    }

    public Long getId() {
        return id;
    }

    public UnidadCambioDeEstado aEvento() {
        return new UnidadCambioDeEstado(
                unidadId,
                nuevoEstado,
                fecha
        );
    }

    public void marcarPublicado() {
        this.publicado = true;
    }
}
