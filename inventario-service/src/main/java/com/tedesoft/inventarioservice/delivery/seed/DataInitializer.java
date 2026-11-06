package com.tedesoft.inventarioservice.delivery.seed;

import com.tedesoft.inventarioservice.domain.entities.EstadoUnidad;
import com.tedesoft.inventarioservice.domain.entities.TipoEquipo;
import com.tedesoft.inventarioservice.domain.entities.Unidad;
import com.tedesoft.inventarioservice.domain.repositories.TipoEquipoRepository;
import com.tedesoft.inventarioservice.domain.repositories.UnidadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Siembra datos de demostración al arrancar: 3 tipos de equipo con reglas distintas y 120 unidades
 * (40 por tipo). Es repetible: cada tipo se busca por nombre y cada unidad por número de inventario,
 * así que reiniciar el servicio, o completar una siembra a medias, nunca duplica registros.
 *
 * <p>Son solo datos: las reglas de cada tipo viajan en el registro del tipo, no en la lógica (RQ-01).
 * No corre con el perfil {@code test} y se puede apagar con {@code inventario.seed.enabled=false}.
 */
@Component
@Profile("!test")
@ConditionalOnProperty(prefix = "inventario.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    static final int UNIDADES_POR_TIPO = 40;

    private record SemillaTipo(String nombre, String descripcion, int duracionMaximaDias,
                               boolean requiereAutorizacion, String prefijo) {
    }

    private static final List<SemillaTipo> TIPOS = List.of(
            new SemillaTipo("Multímetro", "Mide voltaje, corriente y resistencia", 7, false, "MUL"),
            new SemillaTipo("Portátil", "Computador portátil para prácticas", 1, false, "POR"),
            new SemillaTipo("Cámara", "Cámara fotográfica para evidencias y proyectos", 3, true, "CAM"));

    private final TipoEquipoRepository tipoEquipoRepository;
    private final UnidadRepository unidadRepository;

    public DataInitializer(TipoEquipoRepository tipoEquipoRepository, UnidadRepository unidadRepository) {
        this.tipoEquipoRepository = tipoEquipoRepository;
        this.unidadRepository = unidadRepository;
    }

    @Override
    public void run(String... args) {
        List<TipoEquipo> tiposExistentes = tipoEquipoRepository.findAll();
        Set<String> numerosExistentes = new HashSet<>();
        unidadRepository.findAllNumerosInventario()
                .forEach(numero -> numerosExistentes.add(numero.toUpperCase(Locale.ROOT)));

        int tiposCreados = 0;
        int unidadesCreadas = 0;
        for (SemillaTipo semilla : TIPOS) {
            TipoEquipo tipo = tiposExistentes.stream()
                    .filter(t -> t.getNombre().equalsIgnoreCase(semilla.nombre()))
                    .findFirst()
                    .orElse(null);
            if (tipo == null) {
                tipo = tipoEquipoRepository.save(TipoEquipo.crear(semilla.nombre(), semilla.descripcion(),
                        semilla.duracionMaximaDias(), semilla.requiereAutorizacion()));
                tiposCreados++;
            }

            List<Unidad> nuevas = new ArrayList<>();
            for (int n = 1; n <= UNIDADES_POR_TIPO; n++) {
                String numero = String.format(Locale.ROOT, "%s-%03d", semilla.prefijo(), n);
                if (numerosExistentes.add(numero.toUpperCase(Locale.ROOT))) {
                    EstadoUnidad estado = estadoPara(n);
                    nuevas.add(Unidad.crear(tipo, numero, estado, observacionesPara(estado)));
                }
            }
            if (!nuevas.isEmpty()) {
                unidadRepository.saveAll(nuevas);
                unidadesCreadas += nuevas.size();
            }
        }

        if (tiposCreados == 0 && unidadesCreadas == 0) {
            log.info("Siembra omitida: los datos de demostración ya existen");
        } else {
            log.info("Siembra completada: {} tipos y {} unidades creados", tiposCreados, unidadesCreadas);
        }
    }

    /**
     * Reparto fijo por tipo: 36 disponibles, 2 en revisión y 2 fuera de servicio.
     *
     * <p>No se siembra ninguna PRESTADA: ese estado solo llega por el evento de entrega (D-02). Una unidad
     * sembrada como prestada no tendría préstamo en prestamos-service, así que nadie podría devolverla.
     * Los estados no disponibles bastan para demostrar el filtro de disponibles (RF-03).
     */
    private static EstadoUnidad estadoPara(int n) {
        if (n <= 36) {
            return EstadoUnidad.DISPONIBLE;
        }
        if (n <= 38) {
            return EstadoUnidad.EN_REVISION;
        }
        return EstadoUnidad.FUERA_DE_SERVICIO;
    }

    private static String observacionesPara(EstadoUnidad estado) {
        return switch (estado) {
            case EN_REVISION -> "Pendiente de revisión técnica";
            case FUERA_DE_SERVICIO -> "Daño reportado, no se puede prestar";
            default -> "Buen estado";
        };
    }
}
