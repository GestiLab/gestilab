package com.tedesoft.inventarioservice.domain.repositories;

import com.tedesoft.inventarioservice.domain.entities.EstadoUnidad;
import com.tedesoft.inventarioservice.domain.entities.Unidad;
import jakarta.persistence.criteria.Expression;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/**
 * Filtros dinámicos del catálogo (RF-03). Cada filtro es opcional: si no se envía, no restringe. Se
 * combinan con AND.
 */
public final class UnidadSpecification {

    private static final char ESCAPE = '\\';

    private UnidadSpecification() {
    }

    public static Specification<Unidad> filtrar(Long tipoId, EstadoUnidad estado, String nombre) {
        return porTipo(tipoId).and(porEstado(estado)).and(porNombreDeTipo(nombre));
    }

    static Specification<Unidad> porTipo(Long tipoId) {
        return (unidad, consulta, cb) -> tipoId == null ? null : cb.equal(unidad.get("tipo").get("id"), tipoId);
    }

    static Specification<Unidad> porEstado(EstadoUnidad estado) {
        return (unidad, consulta, cb) -> estado == null ? null : cb.equal(unidad.get("estado"), estado);
    }

    /**
     * Coincidencia parcial, sin distinguir mayúsculas, sobre el nombre del tipo. Los comodines SQL que
     * escriba la persona ({@code %} y {@code _}) se escapan: se buscan como texto literal.
     */
    static Specification<Unidad> porNombreDeTipo(String nombre) {
        return (unidad, consulta, cb) -> {
            if (nombre == null || nombre.isBlank()) {
                return null;
            }
            Expression<String> nombreDelTipo = unidad.get("tipo").get("nombre");
            String patron = "%" + escapar(nombre.strip().toLowerCase(Locale.ROOT)) + "%";
            return cb.like(cb.lower(nombreDelTipo), patron, ESCAPE);
        };
    }

    /** Antepone el carácter de escape a {@code \}, {@code %} y {@code _} (la barra primero). */
    static String escapar(String texto) {
        return texto
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
