package com.tedesoft.inventarioservice.delivery.rest;

import com.tedesoft.inventarioservice.delivery.rest.dto.TipoEquipoRequestDto;
import com.tedesoft.inventarioservice.delivery.rest.dto.TipoEquipoResponseDto;
import com.tedesoft.inventarioservice.domain.entities.TipoEquipo;
import com.tedesoft.inventarioservice.domain.services.TipoEquipoService;
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
@RequestMapping("/api/v1/inventario-service/tipos-equipo")
public class TipoEquipoController {

    private final TipoEquipoService service;

    public TipoEquipoController(TipoEquipoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TipoEquipoResponseDto> crear(@Valid @RequestBody TipoEquipoRequestDto request) {
        TipoEquipo creado = service.crear(request.nombre(), request.descripcion(),
                request.duracionMaximaDias(), request.requiereAutorizacion());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.getId())
                .toUri();
        return ResponseEntity.created(location).body(TipoEquipoResponseDto.desde(creado));
    }

    @GetMapping("/{id}")
    public TipoEquipoResponseDto obtener(@PathVariable Long id) {
        return TipoEquipoResponseDto.desde(service.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    public TipoEquipoResponseDto actualizar(@PathVariable Long id,
                                            @Valid @RequestBody TipoEquipoRequestDto request) {
        return TipoEquipoResponseDto.desde(service.actualizar(id, request.nombre(), request.descripcion(),
                request.duracionMaximaDias(), request.requiereAutorizacion()));
    }
}
