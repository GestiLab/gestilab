package com.tedesoft.inventarioservice.delivery.rest;

import com.tedesoft.inventarioservice.delivery.rest.dto.UnidadRequestDto;
import com.tedesoft.inventarioservice.delivery.rest.dto.UnidadResponseDto;
import com.tedesoft.inventarioservice.domain.entities.EstadoUnidad;
import com.tedesoft.inventarioservice.domain.entities.Unidad;
import com.tedesoft.inventarioservice.domain.services.UnidadService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/inventario-service/unidades")
public class UnidadController {

    private final UnidadService service;

    public UnidadController(UnidadService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<UnidadResponseDto> crear(@Valid @RequestBody UnidadRequestDto request) {
        Unidad creada = service.crear(request.tipoEquipoId(), request.numeroInventario(),
                EstadoUnidad.desdeTexto(request.estado()), request.observaciones());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creada.getId())
                .toUri();
        return ResponseEntity.created(location).body(UnidadResponseDto.desde(creada));
    }

    @GetMapping("/{id}")
    public UnidadResponseDto obtener(@PathVariable Long id) {
        return UnidadResponseDto.desde(service.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    public UnidadResponseDto actualizar(@PathVariable Long id, @Valid @RequestBody UnidadRequestDto request) {
        return UnidadResponseDto.desde(service.actualizar(id, request.tipoEquipoId(),
                request.numeroInventario(), EstadoUnidad.desdeTexto(request.estado()), request.observaciones()));
    }
}
