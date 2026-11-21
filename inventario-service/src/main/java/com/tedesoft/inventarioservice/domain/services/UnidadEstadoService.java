package com.tedesoft.inventarioservice.domain.services;

import com.tedesoft.inventarioservice.domain.entities.EstadoUnidad;
import com.tedesoft.inventarioservice.domain.entities.ResultadoEventoEstado;
import com.tedesoft.inventarioservice.domain.entities.Unidad;
import com.tedesoft.inventarioservice.domain.repositories.UnidadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Aplica el evento {@code UnidadCambioDeEstado} que publica {@code prestamos-service} (SCRUM-21).
 * Nunca lanza una excepción por un evento inválido: lo descarta y deja un registro, porque un mensaje
 * que falla se reintentaría para siempre y bloquearía los siguientes.
 */
@Service
@Transactional
public class UnidadEstadoService {

    private static final Logger log = LoggerFactory.getLogger(UnidadEstadoService.class);

    private final UnidadRepository unidadRepository;

    public UnidadEstadoService(UnidadRepository unidadRepository) {
        this.unidadRepository = unidadRepository;
    }

    public ResultadoEventoEstado aplicarCambioDeEstado(Long unidadId, EstadoUnidad nuevoEstado, Instant fechaEvento) {
        Optional<Unidad> encontrada = unidadRepository.findById(unidadId);
        if (encontrada.isEmpty()) {
            log.warn("Evento descartado: la unidad {} no existe", unidadId);
            return ResultadoEventoEstado.UNIDAD_INEXISTENTE;
        }

        Unidad unidad = encontrada.get();
        EstadoUnidad estadoAnterior = unidad.getEstado();
        Instant ultimoEvento = unidad.getUltimoEventoEn();
        ResultadoEventoEstado resultado = unidad.aplicarEventoDeEstado(nuevoEstado, fechaEvento);

        switch (resultado) {
            case APLICADO -> log.info("Unidad {} pasó de {} a {} (evento del {})",
                    unidadId, estadoAnterior, nuevoEstado, fechaEvento);
            case SIN_CAMBIO -> log.info("Evento repetido para la unidad {}: ya estaba {}", unidadId, estadoAnterior);
            case FUERA_DE_ORDEN -> log.warn(
                    "Evento descartado: el del {} no es posterior al último aplicado a la unidad {} ({}); se conserva {}",
                    fechaEvento, unidadId, ultimoEvento, estadoAnterior);
            case NO_APLICABLE -> log.warn("Evento descartado: la unidad {} está {} y no puede pasar a {}",
                    unidadId, estadoAnterior, nuevoEstado);
            case ESTADO_NO_ADMITIDO -> log.warn(
                    "Evento descartado: el estado {} no se aplica a la unidad {} (solo PRESTADA o DISPONIBLE)",
                    nuevoEstado, unidadId);
            case UNIDAD_INEXISTENTE -> { /* no se produce aquí: se resolvió arriba */ }
        }
        return resultado;
    }
}
