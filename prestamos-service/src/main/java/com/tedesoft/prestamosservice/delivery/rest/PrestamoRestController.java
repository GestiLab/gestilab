package com.tedesoft.prestamosservice.delivery.rest;

import com.tedesoft.prestamosservice.delivery.rest.dto.FiltroPersonaDto;
import com.tedesoft.prestamosservice.delivery.rest.dto.PrestamoResponseDto;
import com.tedesoft.prestamosservice.delivery.rest.dto.PrestamoRetrasadoResponseDto;
import com.tedesoft.prestamosservice.delivery.rest.dto.SolicitudPrestamoRequestDto;
import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.exception.FotoInvalidaException;
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
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.UncheckedIOException;

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


    /**
     * Registra la entrega (SOLICITADO a ENTREGADO). La fotografía es obligatoria (SCRUM-22, CA-5): llega como
     * la parte {@code foto} de una petición multipart. Sin ella responde 400 y el préstamo no cambia de estado.
     */
    @PatchMapping("/{id}/entregar")
    public ResponseEntity<PrestamoResponseDto> entregar(
            @PathVariable Long id,
            @RequestParam(name = "foto", required = false) MultipartFile foto) {

        exigirFotografia(foto);

        return ResponseEntity.ok(PrestamoResponseDto.desde(
                prestamoService.entregarConFoto(id, leerFotografia(foto), foto.getContentType())));
    }

    /** Registra la devolución (ENTREGADO a DEVUELTO), también con fotografía obligatoria. */
    @PatchMapping("/{id}/recibir")
    public ResponseEntity<PrestamoResponseDto> recibir(
            @PathVariable Long id,
            @RequestParam(name = "foto", required = false) MultipartFile foto) {

        exigirFotografia(foto);

        return ResponseEntity.ok(PrestamoResponseDto.desde(
                prestamoService.recibirConFoto(id, leerFotografia(foto), foto.getContentType())));
    }

    /** Sin la parte {@code foto} (o sin petición multipart) Spring la entrega como null: se rechaza aquí. */
    private void exigirFotografia(MultipartFile foto) {
        if (foto == null) {
            throw new FotoInvalidaException("La fotografía es obligatoria");
        }
    }

    private byte[] leerFotografia(MultipartFile foto) {
        try {
            return foto.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo leer la fotografía", ex);
        }
    }
}
