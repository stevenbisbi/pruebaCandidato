package com.conciliacion.pagos.adapters.inbound.rest.dto;

import java.util.List;

/**
 * Pagina por cursor (D-03): no trae total ni numero de pagina. La siguiente se pide pasando
 * nextCursor en el parametro cursor; si es null, no hay mas.
 */
public record CursorPageResponseDto<T>(List<T> items, int size, String nextCursor, boolean hasNext) {
}
