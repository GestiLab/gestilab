package com.tedesoft.prestamosservice.delivery.rest;

import com.tedesoft.prestamosservice.delivery.rest.dto.PrestamoResponseDto;
import com.tedesoft.prestamosservice.delivery.rest.dto.SolicitudPrestamoRequestDto;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.services.IPrestamoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/${api.version}/prestamos-service/prestamos")
@RequiredArgsConstructor
public class PrestamoRestController {

    private final IPrestamoService prestamoService;

    @PostMapping
    public ResponseEntity<PrestamoResponseDto> solicitar(@Valid @RequestBody SolicitudPrestamoRequestDto request) {
        // Unidad no disponible -> 409, inexistente -> 404, inventario caído -> 503: GlobalExceptionHandler
        Prestamo creado = prestamoService.solicitar(request.unidadId(), request.solicitanteId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.getId())
                .toUri();
        return ResponseEntity.created(location).body(PrestamoResponseDto.desde(creado));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PrestamoResponseDto> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(PrestamoResponseDto.desde(prestamoService.obtenerPorId(id)));
    }
}
