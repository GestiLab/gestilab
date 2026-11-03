package com.tedesoft.inventarioservice.domain.services;

import com.tedesoft.inventarioservice.domain.entities.TipoEquipo;
import com.tedesoft.inventarioservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.inventarioservice.domain.repositories.TipoEquipoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta los casos de uso de los tipos de equipo. No contiene reglas de préstamo: esas viven en
 * {@link TipoEquipo} y en los datos de cada tipo, por lo que un tipo nuevo no requiere cambios aquí.
 * Recibe y devuelve tipos del dominio: no conoce los DTO de la capa {@code delivery}.
 */
@Service
@Transactional
public class TipoEquipoService {

    private final TipoEquipoRepository repository;

    public TipoEquipoService(TipoEquipoRepository repository) {
        this.repository = repository;
    }

    public TipoEquipo crear(String nombre, String descripcion, int duracionMaximaDias,
                            boolean requiereAutorizacion) {
        return repository.save(TipoEquipo.crear(nombre, descripcion, duracionMaximaDias, requiereAutorizacion));
    }

    @Transactional(readOnly = true)
    public TipoEquipo obtenerPorId(Long id) {
        return buscar(id);
    }

    public TipoEquipo actualizar(Long id, String nombre, String descripcion, int duracionMaximaDias,
                                 boolean requiereAutorizacion) {
        TipoEquipo tipo = buscar(id);
        tipo.actualizar(nombre, descripcion, duracionMaximaDias, requiereAutorizacion);
        return repository.save(tipo);
    }

    private TipoEquipo buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Tipo de equipo " + id + " no encontrado"));
    }
}
