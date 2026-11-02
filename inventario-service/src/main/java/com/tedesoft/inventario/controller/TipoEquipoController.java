package com.tedesoft.inventario.controller;

import com.tedesoft.inventario.dto.TipoEquipoRequestDto;
import com.tedesoft.inventario.dto.TipoEquipoResponseDto;
import com.tedesoft.inventario.service.TipoEquipoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/tipos-equipo")
@RequiredArgsConstructor
public class TipoEquipoController {

    private final TipoEquipoService service;

    @PostMapping
    public ResponseEntity<TipoEquipoResponseDto> crear(@Valid @RequestBody TipoEquipoRequestDto request) {
        TipoEquipoResponseDto creado = service.crear(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.id())
                .toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @GetMapping("/{id}")
    public TipoEquipoResponseDto obtener(@PathVariable Long id) {
        return service.obtenerPorId(id);
    }

    @PutMapping("/{id}")
    public TipoEquipoResponseDto actualizar(@PathVariable Long id,
                                            @Valid @RequestBody TipoEquipoRequestDto request) {
        return service.actualizar(id, request);
    }
}
