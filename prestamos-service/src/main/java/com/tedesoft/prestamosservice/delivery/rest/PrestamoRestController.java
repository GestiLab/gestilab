package com.tedesoft.prestamosservice.delivery.rest;

import com.tedesoft.prestamosservice.delivery.rest.dto.PrestamoResponseDto;
import com.tedesoft.prestamosservice.delivery.rest.dto.PrestamoRetrasadoResponseDto;
import com.tedesoft.prestamosservice.delivery.rest.dto.SolicitudPrestamoRequestDto;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.services.IPrestamoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
// produces: sin él, un Accept que no sea JSON se descubre DESPUÉS de crear el préstamo y el cliente recibe 406
@RequestMapping(path = "/api/${api.version}/prestamos-service/prestamos", produces = MediaType.APPLICATION_JSON_VALUE)
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

    /** Solo del auxiliar (RF-08). La restricción por rol llega con la historia de seguridad (SCRUM-20, CA-13). */
    @GetMapping("/retrasos")
    public ResponseEntity<List<PrestamoRetrasadoResponseDto>> listarRetrasos() {
        return ResponseEntity.ok(prestamoService.listarRetrasados().stream()
                .map(PrestamoRetrasadoResponseDto::desde)
                .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PrestamoResponseDto> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(PrestamoResponseDto.desde(prestamoService.obtenerPorId(id)));
    }
}
