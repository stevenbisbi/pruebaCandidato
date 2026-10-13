package com.conciliacion.pagos.domain.model;

import java.util.List;

/**
 * Una pagina del resultado de un lote (RF-21, RF-22).
 *
 * @param totalItems cuantas lineas cumplen el filtro en total, para saber cuantas paginas hay
 */
public record LinePage(List<LineResult> items, int page, int size, long totalItems) {

    public boolean hasNext() {
        return (long) (page + 1) * size < totalItems;
    }
}
