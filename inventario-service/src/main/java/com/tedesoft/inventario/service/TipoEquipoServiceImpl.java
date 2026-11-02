package com.tedesoft.inventario.service;

import com.tedesoft.inventario.domain.TipoEquipo;
import com.tedesoft.inventario.dto.TipoEquipoRequestDto;
import com.tedesoft.inventario.dto.TipoEquipoResponseDto;
import com.tedesoft.inventario.exception.TipoEquipoNoEncontradoException;
import com.tedesoft.inventario.repository.TipoEquipoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta los casos de uso. No contiene reglas de préstamo: esas viven en {@link TipoEquipo}
 * y en los datos de cada tipo, por lo que un tipo nuevo no requiere cambios aquí (CA-6).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TipoEquipoServiceImpl implements TipoEquipoService {

    private final TipoEquipoRepository repository;

    @Override
    public TipoEquipoResponseDto crear(TipoEquipoRequestDto request) {
        TipoEquipo tipo = TipoEquipo.crear(
                request.nombre(),
                request.descripcion(),
                request.duracionMaximaDias(),
                request.requiereAutorizacionPrevia());
        return TipoEquipoResponseDto.desde(repository.save(tipo));
    }

    @Override
    @Transactional(readOnly = true)
    public TipoEquipoResponseDto obtenerPorId(Long id) {
        return TipoEquipoResponseDto.desde(buscar(id));
    }

    @Override
    public TipoEquipoResponseDto actualizar(Long id, TipoEquipoRequestDto request) {
        TipoEquipo tipo = buscar(id);
        tipo.actualizar(
                request.nombre(),
                request.descripcion(),
                request.duracionMaximaDias(),
                request.requiereAutorizacionPrevia());
        return TipoEquipoResponseDto.desde(repository.save(tipo));
    }

    private TipoEquipo buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> new TipoEquipoNoEncontradoException(id));
    }
}
