package com.tedesoft.prestamosservice.delivery.rest;

import com.tedesoft.prestamosservice.delivery.rest.dto.FiltroPersonaDto;
import com.tedesoft.prestamosservice.delivery.rest.dto.PrestamoResponseDto;
import com.tedesoft.prestamosservice.delivery.rest.dto.PrestamoRetrasadoResponseDto;
import com.tedesoft.prestamosservice.delivery.rest.dto.SolicitudPrestamoRequestDto;
import com.tedesoft.prestamosservice.delivery.security.Roles;
import com.tedesoft.prestamosservice.domain.clients.IAlmacenamientoFotos;
import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.exception.FotoInvalidaException;
import com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.prestamosservice.domain.services.IPrestamoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
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

    /** Solo del auxiliar (RF-08): la regla vive en {@code SeguridadConfig}; el usuario recibe 403 (SCRUM-20, CA-13). */
    @GetMapping("/retrasos")
    public ResponseEntity<List<PrestamoRetrasadoResponseDto>> listarRetrasos() {
        return ResponseEntity.ok(prestamoService.listarRetrasados().stream()
                .map(PrestamoRetrasadoResponseDto::desde)
                .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PrestamoResponseDto> obtener(@PathVariable Long id, Authentication autenticacion) {
        return ResponseEntity.ok(PrestamoResponseDto.desde(prestamoVisiblePara(id, autenticacion)));
    }

    /**
     * El auxiliar consulta cualquier préstamo (y recibe 404 si no existe). El usuario solo los suyos: si el
     * préstamo es de otra persona <b>o no existe</b> recibe el mismo 403, para que no pueda averiguar qué
     * números de préstamo existen (SCRUM-20, CA-6).
     */
    private Prestamo prestamoVisiblePara(Long id, Authentication autenticacion) {
        if (Roles.esAuxiliar(autenticacion)) {
            return prestamoService.obtenerPorId(id);
        }
        try {
            Prestamo prestamo = prestamoService.obtenerPorId(id);
            if (prestamo.getSolicitanteId().equals(autenticacion.getName())) {
                return prestamo;
            }
        } catch (RecursoNoEncontradoException noExiste) {
            // se responde igual que un préstamo ajeno
        }
        throw new AccessDeniedException("préstamo ajeno o inexistente");
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

    /**
     * Fotografía de la entrega (SCRUM-22, CA-3). Devuelve la imagen tal cual con su tipo (PNG o JPEG); Angular la
     * baja con HttpClient y el token, porque un {@code <img>} no puede enviar el encabezado Authorization.
     * {@code produces = ALL}: los errores (404, 503) siguen siendo JSON aunque el cliente pida una imagen.
     */
    @GetMapping(path = "/{id}/foto-entrega", produces = MediaType.ALL_VALUE)
    public ResponseEntity<byte[]> fotoEntrega(@PathVariable Long id) {
        return imagen(prestamoService.obtenerFotoEntrega(id));
    }

    /** Fotografía de la devolución: solo existe en un préstamo devuelto. */
    @GetMapping(path = "/{id}/foto-devolucion", produces = MediaType.ALL_VALUE)
    public ResponseEntity<byte[]> fotoDevolucion(@PathVariable Long id) {
        return imagen(prestamoService.obtenerFotoDevolucion(id));
    }

    private ResponseEntity<byte[]> imagen(IAlmacenamientoFotos.Foto foto) {
        MediaType tipo = foto.tipoMime() == null || foto.tipoMime().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(foto.tipoMime());
        return ResponseEntity.ok()
                .contentType(tipo)
                // evidencia de un préstamo: que ningún intermediario la guarde en caché
                .cacheControl(CacheControl.noStore())
                .body(foto.contenido());
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
