package com.tedesoft.inventarioservice.domain.repositories;

import com.tedesoft.inventarioservice.domain.entities.Unidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UnidadRepository extends JpaRepository<Unidad, Long> {

    boolean existsByNumeroInventarioIgnoreCase(String numeroInventario);

    boolean existsByNumeroInventarioIgnoreCaseAndIdNot(String numeroInventario, Long id);

    /** Solo los números (sin cargar entidades): lo usa el inicializador para no duplicar la siembra. */
    @Query("select u.numeroInventario from Unidad u")
    List<String> findAllNumerosInventario();
}
