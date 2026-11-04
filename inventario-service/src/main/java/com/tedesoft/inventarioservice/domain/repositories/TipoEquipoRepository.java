package com.tedesoft.inventarioservice.domain.repositories;

import com.tedesoft.inventarioservice.domain.entities.TipoEquipo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoEquipoRepository extends JpaRepository<TipoEquipo, Long> {

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
