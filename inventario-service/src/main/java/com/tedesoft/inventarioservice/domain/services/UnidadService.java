package com.tedesoft.inventarioservice.domain.services;

import com.tedesoft.inventarioservice.domain.entities.EstadoUnidad;
import com.tedesoft.inventarioservice.domain.entities.TipoEquipo;
import com.tedesoft.inventarioservice.domain.entities.Unidad;
import com.tedesoft.inventarioservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.inventarioservice.domain.exception.ReglaNegocioException;
import com.tedesoft.inventarioservice.domain.repositories.TipoEquipoRepository;
import com.tedesoft.inventarioservice.domain.repositories.UnidadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta los casos de uso de las unidades. No contiene reglas de préstamo: esas viven en el
 * {@link TipoEquipo} al que pertenece cada unidad. Recibe y devuelve entidades del dominio: no conoce
 * los DTO de la capa {@code delivery}.
 */
@Service
@Transactional
public class UnidadService {

    private final UnidadRepository unidadRepository;
    private final TipoEquipoRepository tipoEquipoRepository;

    public UnidadService(UnidadRepository unidadRepository, TipoEquipoRepository tipoEquipoRepository) {
        this.unidadRepository = unidadRepository;
        this.tipoEquipoRepository = tipoEquipoRepository;
    }

    public Unidad crear(Long tipoEquipoId, String numeroInventario, EstadoUnidad estado, String observaciones) {
        // Primero el tipo (404 si no existe): así se aborta antes de tocar nada más.
        TipoEquipo tipo = buscarTipo(tipoEquipoId);
        // El dominio valida y normaliza (strip) el número antes de buscar duplicados.
        Unidad nueva = Unidad.crear(tipo, numeroInventario, estado, observaciones);
        if (unidadRepository.existsByNumeroInventarioIgnoreCase(nueva.getNumeroInventario())) {
            throw numeroDuplicado(nueva.getNumeroInventario());
        }
        return unidadRepository.save(nueva);
    }

    @Transactional(readOnly = true)
    public Unidad obtenerPorId(Long id) {
        return buscar(id);
    }

    public Unidad actualizar(Long id, Long tipoEquipoId, String numeroInventario, EstadoUnidad estado,
                             String observaciones) {
        Unidad unidad = buscar(id);
        TipoEquipo tipo = buscarTipo(tipoEquipoId);
        // Se consulta antes de modificar la entidad gestionada para no forzar un flush con datos a medias.
        if (numeroInventario != null && !numeroInventario.isBlank()
                && unidadRepository.existsByNumeroInventarioIgnoreCaseAndIdNot(numeroInventario.strip(), id)) {
            throw numeroDuplicado(numeroInventario.strip());
        }
        unidad.actualizar(tipo, numeroInventario, estado, observaciones);
        return unidad;
    }

    private ReglaNegocioException numeroDuplicado(String numeroInventario) {
        return new ReglaNegocioException(
                "Ya existe una unidad con el número de inventario \"" + numeroInventario + "\"");
    }

    private Unidad buscar(Long id) {
        return unidadRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Unidad " + id + " no encontrada"));
    }

    private TipoEquipo buscarTipo(Long tipoEquipoId) {
        return tipoEquipoRepository.findById(tipoEquipoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Tipo de equipo " + tipoEquipoId + " no encontrado"));
    }
}
