package com.conciliacion.pagos.domain.model;

import java.util.List;

/** Una pagina del resultado de un lote. */
public record LinePage(List<LineResult> items, int page, int size, long totalItems) {
}
