package com.conciliacion.pagos.application.query;

import com.conciliacion.pagos.domain.model.LineResult;

import java.util.List;

public record LinePage(List<LineResult> items, int page, int size, long totalItems) {
}
