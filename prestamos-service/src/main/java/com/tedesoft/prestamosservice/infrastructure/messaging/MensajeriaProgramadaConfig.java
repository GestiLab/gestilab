package com.tedesoft.prestamosservice.infrastructure.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Sin {@code @EnableScheduling} Spring no ejecuta ningún {@code @Scheduled}: el buzón de eventos pendientes
 * se llenaría y nunca se publicaría. Se activa junto con el procesador.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "gestilab.eventos.procesador.habilitado", havingValue = "true", matchIfMissing = true)
public class MensajeriaProgramadaConfig {
}
