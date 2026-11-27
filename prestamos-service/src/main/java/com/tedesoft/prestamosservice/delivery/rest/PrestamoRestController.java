
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
import java.util.regex.Pattern;

@RestController
// produces: sin él, un Accept que no sea JSON se descubre DESPUÉS de crear el préstamo y el cliente recibe 406
@RequestMapping(path = "/api/${api.version}/prestamos-service/prestamos", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PrestamoRestController {

    /** El largo de la columna {@code solicitante_id}. */
    private static final int LARGO_MAXIMO_SOLICITANTE = 100;
    /** Categoría Unicode Cc: los controles ASCII y también los C1 (U+0080 a U+009F), que {@code \p{Cntrl}} no ve. */
    private static final Pattern CARACTER_DE_CONTROL = Pattern.compile("\\p{Cc}");

    private final IPrestamoService prestamoService;

    @PostMapping
    public ResponseEntity<PrestamoResponseDto> solicitar(@Valid @RequestBody SolicitudPrestamoRequestDto request,
                                                         Authentication autenticacion) {
        // Quién solicita es el sub del token (SCRUM-24, CA-2): el cuerpo no lo trae y, si lo trae, se ignora.
        // Unidad no disponible -> 409, inexistente -> 404, inventario caído -> 503: GlobalExceptionHandler
        Prestamo creado = prestamoService.solicitar(request.unidadId(), solicitanteDelToken(autenticacion));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.getId())
                .toUri();
        return ResponseEntity.created(location).body(PrestamoResponseDto.desde(creado));
    }

    /**
     * La persona cuyos préstamos se listan. El auxiliar pide la que quiera (o ninguna: todos). El usuario solo puede
     * pedir la suya: la otra se rechaza aquí, antes de consultar, para que el 403 sea idéntico exista o no la persona.
     */
    private static String personaVisiblePara(String pedida, Authentication autenticacion) {
        if (Roles.esAuxiliar(autenticacion)) {
            return pedida;
        }
        String propia = solicitanteDelToken(autenticacion);
        if (pedida == null || pedida.equals(propia)) {
            return propia;
        }
        throw new AccessDeniedException("préstamos de otra persona");
    }

    /**
     * Quién solicita es el {@code sub} del token, y se usa tal cual: no se recorta. Se rechaza con 403 (en lugar de
     * guardar un préstamo sin dueño o con otro dueño) el que no identifica a nadie de forma estable:
     * <ul>
     *   <li>en blanco o ausente;</li>
     *   <li>con espacios en los extremos: el dominio lo guardaría recortado y " ana" acabaría siendo "ana";</li>
     *   <li>de más de 100 caracteres: no cabe en {@code solicitante_id} y el fallo al guardar parecería un 409;</li>
     *   <li>con caracteres de control, igual que el filtro {@code persona}.</li>
     * </ul>
     * Con Keycloak el {@code sub} es un UUID de 36 caracteres, así que ninguno de estos casos debería darse.
     */
    private static String solicitanteDelToken(Authentication autenticacion) {
        String sub = autenticacion.getName();
        if (sub == null || sub.isBlank() || !sub.equals(sub.strip())
                || sub.length() > LARGO_MAXIMO_SOLICITANTE || CARACTER_DE_CONTROL.matcher(sub).find()) {
            throw new AccessDeniedException("el token no trae un sub utilizable");
        }
        return sub;
    }

    /**
     * Lista préstamos, el más reciente primero (SCRUM-24). Cada rol ve distinto:
     * <ul>
     *   <li><b>Auxiliar:</b> los de cualquier persona ({@code persona}) o de todas, y filtrables por {@code estado}.</li>
     *   <li><b>Usuario:</b> solo los suyos: sin {@code persona} o con su propio sub, los suyos; con la de otra
     *       persona, 403, igual exista o no y tenga o no préstamos, y sin consultar la base.</li>
     * </ul>
     */
    @GetMapping
    public ResponseEntity<List<PrestamoResponseDto>> listar(
            @Valid FiltroPersonaDto filtro,
            @RequestParam(required = false) EstadoPrestamo estado,
            Authentication autenticacion) {
        String persona = personaVisiblePara(filtro.persona(), autenticacion);
        return ResponseEntity.ok(prestamoService.listar(persona, estado).stream()
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
        // Antes de consultar: un sub no utilizable recibe el mismo 403 sin tocar la base
        String propio = solicitanteDelToken(autenticacion);
        try {
            Prestamo prestamo = prestamoService.obtenerPorId(id);
            if (prestamo.getSolicitanteId().equals(propio)) {
                return prestamo;
            }
        } catch (RecursoNoEncontradoException noExiste) {
            // se responde igual que un préstamo ajeno
        }
        throw new AccessDeniedException("préstamo ajeno o inexistente");
    }


    /**
     * Autoriza una solicitud pendiente (PENDIENTE_AUTORIZACION a SOLICITADO).
     * Solo el auxiliar puede realizar esta operación según SeguridadConfig.
     *
     * Conserva las fechas originales del préstamo y no publica eventos para inventario.
     * Si el préstamo no existe responde 404; si su estado no permite autorizarlo, responde 409.
     */
    @PatchMapping("/{id}/autorizar")
    public ResponseEntity<PrestamoResponseDto> autorizar(@PathVariable Long id) {
        return ResponseEntity.ok(PrestamoResponseDto.desde(
                prestamoService.autorizar(id)));
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
