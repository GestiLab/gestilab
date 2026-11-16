package com.tedesoft.inventarioservice.domain.repositories;

import com.tedesoft.inventarioservice.domain.entities.EstadoUnidad;
import com.tedesoft.inventarioservice.domain.entities.Unidad;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

/**
 * Filtros dinámicos del catálogo (RF-03). Cada filtro es opcional: si no se envía, no restringe. Se
 * combinan con AND. La búsqueda por nombre se resuelve antes, en el dominio
 * ({@code TipoEquipo.nombreContiene}), y llega aquí como la lista de tipos que coinciden.
 */
public final class UnidadSpecification {

    private UnidadSpecification() {
    }

    /**
     * @param tiposPorNombre ids de los tipos cuyo nombre coincide con la búsqueda; {@code null} si no
     *                       se buscó por nombre. Una lista vacía significa que ningún tipo coincide.
     */
    public static Specification<Unidad> filtrar(Long tipoId, EstadoUnidad estado,
                                                Collection<Long> tiposPorNombre) {
        return porTipo(tipoId).and(porEstado(estado)).and(entreTipos(tiposPorNombre));
    }

    static Specification<Unidad> porTipo(Long tipoId) {
        return (unidad, consulta, cb) -> tipoId == null ? null : cb.equal(unidad.get("tipo").get("id"), tipoId);
    }

    static Specification<Unidad> porEstado(EstadoUnidad estado) {
        return (unidad, consulta, cb) -> estado == null ? null : cb.equal(unidad.get("estado"), estado);
    }

    /** Sin búsqueda no restringe; con búsqueda sin coincidencias no devuelve nada (un IN vacío no es SQL válido). */
    static Specification<Unidad> entreTipos(Collection<Long> tipoIds) {
        return (unidad, consulta, cb) -> {
            if (tipoIds == null) {
                return null;
            }
            return tipoIds.isEmpty() ? cb.disjunction() : unidad.get("tipo").get("id").in(tipoIds);
        };
    }
}
