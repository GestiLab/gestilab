package com.tedesoft.inventarioservice.domain.services;

import com.tedesoft.inventarioservice.domain.entities.TipoEquipo;
import com.tedesoft.inventarioservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.inventarioservice.domain.exception.ReglaNegocioException;
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
        // Se crea primero: el dominio valida y normaliza (strip) el nombre antes de buscar duplicados
        TipoEquipo nuevo = TipoEquipo.crear(nombre, descripcion, duracionMaximaDias, requiereAutorizacion);
        if (repository.existsByNombreIgnoreCase(nuevo.getNombre())) {
            throw nombreDuplicado(nuevo.getNombre());
        }
        return repository.save(nuevo);
    }

    @Transactional(readOnly = true)
    public TipoEquipo obtenerPorId(Long id) {
        return buscar(id);
    }

    public TipoEquipo actualizar(Long id, String nombre, String descripcion, int duracionMaximaDias,
                                 boolean requiereAutorizacion) {
        TipoEquipo tipo = buscar(id);
        // Se consulta antes de modificar la entidad gestionada para no forzar un flush con datos a medias
        if (nombre != null && !nombre.isBlank()
                && repository.existsByNombreIgnoreCaseAndIdNot(nombre.strip(), id)) {
            throw nombreDuplicado(nombre.strip());
        }
        tipo.actualizar(nombre, descripcion, duracionMaximaDias, requiereAutorizacion);
        return tipo;
    }

    private ReglaNegocioException nombreDuplicado(String nombre) {
        return new ReglaNegocioException("Ya existe un tipo de equipo con el nombre \"" + nombre + "\"");
    }

    private TipoEquipo buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Tipo de equipo " + id + " no encontrado"));
    }
}
