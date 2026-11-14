package com.tedesoft.prestamosservice.domain.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class FechaServiceImpl implements IFechaService {

    private final Clock clock;

    @Override
    public LocalDate hoy() {
        return LocalDate.ofInstant(clock.instant(), clock.getZone());
    }

    @Override
    public OffsetDateTime ahora() {
        return OffsetDateTime.ofInstant(clock.instant(), clock.getZone());
    }
}
