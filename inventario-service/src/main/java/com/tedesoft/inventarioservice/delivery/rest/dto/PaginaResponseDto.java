package com.tedesoft.inventarioservice.delivery.rest.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Página de resultados con los campos que consume el cliente Angular ({@code content}, {@code number},
 * {@code totalElements}) más {@code size} y {@code totalPages}. Es un DTO propio: serializar un
 * {@code PageImpl} tal cual no es una forma estable de JSON.
 */
public record PaginaResponseDto<T>(
        List<T> content,
        int number,
        int size,
        long totalElements,
        int totalPages
) {

    public static <E, T> PaginaResponseDto<T> desde(Page<E> pagina, Function<E, T> convertir) {
        return new PaginaResponseDto<>(
                pagina.getContent().stream().map(convertir).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages());
    }
}
