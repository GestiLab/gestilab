package com.tedesoft.prestamosservice.infrastructure.persistence;

import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Una unidad no puede tener dos préstamos abiertos. Las anotaciones de JPA no pueden declarar un índice único
 * parcial, así que se crea con SQL propio de PostgreSQL al arrancar (después de que Hibernate crea la tabla).
 * Los estados "abiertos" salen de {@link EstadoPrestamo#activos()}: si se agrega uno (autorización previa),
 * el índice lo incluye porque se recrea en cada arranque (DROP y CREATE; el servicio aún no atiende peticiones).
 *
 * <p>Si la base ya tiene dos préstamos abiertos de la misma unidad, el índice no se puede crear: se registra
 * un ERROR con el SQL y el servicio arranca igual (la comprobación en código sigue activa).
 *
 * <p>La autorización previa también exige actualizar la restricción CHECK de la columna estado,
 * porque Hibernate no actualiza automáticamente esa restricción en una base existente. A diferencia del
 * índice, si esa migración falla el servicio <b>no arranca</b> (decisión de SCRUM-31): con un CHECK viejo
 * la base rechazaría PENDIENTE_AUTORIZACION y la solicitud respondería un 409 engañoso («unidad ya prestada»). Como el índice, se aplica en cada arranque.
 */
@Slf4j
@Component
public class IndiceUnicoPrestamoAbierto implements ApplicationRunner {

    static final String NOMBRE = "ux_prestamo_unidad_abierto";

    private final JdbcTemplate jdbc;

    public IndiceUnicoPrestamoAbierto(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static String sqlEliminar() {
        return "DROP INDEX IF EXISTS " + NOMBRE;
    }

    static String sql() {
        String estados = EstadoPrestamo.activos().stream()
                .map(e -> "'" + e.name() + "'")
                .collect(Collectors.joining(", "));
        return "CREATE UNIQUE INDEX " + NOMBRE
                + " ON prestamo (unidad_id) WHERE estado IN (" + estados + ")";
    }

    /**
     * Actualiza el CHECK de estado con todos los valores del enum,
     * incluidos los préstamos cerrados.
     *
     * El DROP y el ADD se ejecutan en una sola sentencia ALTER TABLE,
     * sin eliminar registros existentes.
     */
    static String sqlActualizarCheck() {
        String estados = Arrays.stream(EstadoPrestamo.values())
                .map(e -> "'" + e.name() + "'")
                .collect(Collectors.joining(", "));

        return "ALTER TABLE prestamo "
                + "DROP CONSTRAINT IF EXISTS prestamo_estado_check, "
                + "ADD CONSTRAINT prestamo_estado_check "
                + "CHECK (estado IN (" + estados + "))";
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!ControlConcurrenciaTransaccional.BaseDeDatos.esPostgres(jdbc)) {
            return;
        }

        // Actualizar primero los estados admitidos por PostgreSQL.
        // Si falla la migración, el servicio no debe arrancar con un CHECK incompatible.
        jdbc.execute(sqlActualizarCheck());

        log.info("Restricción CHECK de estados de préstamo actualizada");

        try {
            jdbc.execute(sqlEliminar());
            jdbc.execute(sql());
            log.info("Índice único de préstamos abiertos listo: {}", NOMBRE);
        } catch (Exception e) {
            log.error("No se pudo crear el índice único de préstamos abiertos (¿hay una unidad con dos préstamos "
                    + "abiertos?). Corrige los datos y ejecuta: {}", sql(), e);
        }
    }
}