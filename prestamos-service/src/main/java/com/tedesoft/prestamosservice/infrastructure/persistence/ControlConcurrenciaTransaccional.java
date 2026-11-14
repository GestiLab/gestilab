package com.tedesoft.prestamosservice.infrastructure.persistence;

import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.services.IControlConcurrencia;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.function.Supplier;

/**
 * Transacción con un bloqueo asesor por clave en PostgreSQL ({@code pg_advisory_xact_lock}): quien llega
 * segundo espera a que el primero confirme y entonces ve su préstamo al contar. Se libera solo al terminar
 * la transacción. En otras bases (H2 de las pruebas) solo hay transacción: no hay concurrencia real que cubrir.
 */
@Component
public class ControlConcurrenciaTransaccional implements IControlConcurrencia {

    private final TransactionTemplate transaccion;
    private final JdbcTemplate jdbc;
    private final boolean esPostgres;

    public ControlConcurrenciaTransaccional(PlatformTransactionManager manager, JdbcTemplate jdbc) {
        this.transaccion = new TransactionTemplate(manager);
        this.jdbc = jdbc;
        this.esPostgres = BaseDeDatos.esPostgres(jdbc);
    }

    @Override
    public Prestamo conExclusividad(String clave, Supplier<Prestamo> accion) {
        return transaccion.execute(estado -> {
            if (esPostgres) {
                jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))::text", String.class, clave);
            }
            return accion.get();
        });
    }

    /** Detecta el motor una sola vez. */
    static final class BaseDeDatos {
        static boolean esPostgres(JdbcTemplate jdbc) {
            try {
                String producto = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName());
                return "PostgreSQL".equalsIgnoreCase(producto);
            } catch (Exception e) {
                throw new IllegalStateException(new SQLException("No se pudo detectar la base de datos", e));
            }
        }
    }
}
