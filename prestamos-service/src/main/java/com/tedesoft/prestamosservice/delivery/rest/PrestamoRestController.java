package com.tedesoft.prestamosservice.delivery.rest;

import com.tedesoft.prestamosservice.delivery.rest.dto.FiltroPersonaDto;
import com.tedesoft.prestamosservice.delivery.rest.dto.PrestamoResponseDto;
import com.tedesoft.prestamosservice.delivery.rest.dto.PrestamoRetrasadoResponseDto;
import com.tedesoft.prestamosservice.delivery.rest.dto.SolicitudPrestamoRequestDto;
import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.bind.annotation.PatchMapping;

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

    /**
     * Lista préstamos, el más reciente primero. Con {@code persona} solo los de esa persona; con {@code estado}
     * solo los de ese estado. Provisional: con la seguridad (SCRUM-20) "mis préstamos" saldrá del token y
     * ver los de otra persona quedará reservado al auxiliar.
     */
    @GetMapping
    public ResponseEntity<List<PrestamoResponseDto>> listar(
            @Valid FiltroPersonaDto filtro,
            @RequestParam(required = false) EstadoPrestamo estado) {
        return ResponseEntity.ok(prestamoService.listar(filtro.persona(), estado).stream()
                .map(PrestamoResponseDto::desde)
                .toList());
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


    // SCRUM-19: Registrar entrega de una unidad
    @PatchMapping("/{id}/entregar")
    public ResponseEntity<PrestamoResponseDto> entregar(
            @PathVariable Long id) {

        Prestamo prestamo = prestamoService.entregar(id);

        return ResponseEntity.ok(
                PrestamoResponseDto.desde(prestamo)
        );
    }

    // SCRUM-19: Registrar devolución de una unidad
    @PatchMapping("/{id}/recibir")
    public ResponseEntity<PrestamoResponseDto> recibir(
            @PathVariable Long id) {

        Prestamo prestamo = prestamoService.recibir(id);

        return ResponseEntity.ok(
                PrestamoResponseDto.desde(prestamo)
        );
    }

}
