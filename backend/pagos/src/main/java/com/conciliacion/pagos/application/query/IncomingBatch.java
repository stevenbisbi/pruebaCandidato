package com.conciliacion.pagos.application.query;

import com.conciliacion.pagos.domain.model.BatchLine;

import java.time.LocalDate;
import java.util.List;

/** @param id identificador enviado por Tesoreria; null en CSV sin id, se deriva del contenido */
public record IncomingBatch(String id, String source, String channel, LocalDate generatedOn, List<BatchLine> lines) {
}
