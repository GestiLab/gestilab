package com.tedesoft.inventario.service;

import com.tedesoft.inventario.dto.TipoEquipoRequestDto;
import com.tedesoft.inventario.dto.TipoEquipoResponseDto;

public interface TipoEquipoService {

    TipoEquipoResponseDto crear(TipoEquipoRequestDto request);

    /** @throws com.tedesoft.inventario.exception.TipoEquipoNoEncontradoException si el id no existe */
    TipoEquipoResponseDto obtenerPorId(Long id);

    /** @throws com.tedesoft.inventario.exception.TipoEquipoNoEncontradoException si el id no existe */
    TipoEquipoResponseDto actualizar(Long id, TipoEquipoRequestDto request);
}
