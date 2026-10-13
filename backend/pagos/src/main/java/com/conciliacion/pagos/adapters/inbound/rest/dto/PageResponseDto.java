package com.conciliacion.pagos.adapters.inbound.rest.dto;

import java.util.List;

/** Pagina generica de la API: los elementos y lo necesario para pedir la siguiente. */
public record PageResponseDto<T>(List<T> items, int page, int size, long totalItems, boolean hasNext) {
}
