package com.tedesoft.prestamosservice.domain.repositories;

import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IPrestamoDao extends JpaRepository<Prestamo, Long> {
}
