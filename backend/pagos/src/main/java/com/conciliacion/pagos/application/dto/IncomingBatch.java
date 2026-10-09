package com.conciliacion.pagos.application.dto;

import com.conciliacion.pagos.domain.model.BatchLine;

import java.util.List;

/**
 * Un lote recien recibido, todavia sin procesar.
 *
 * @param id identificador enviado junto al archivo, o null para derivarlo del contenido
 * @param source quien envia el lote, por ejemplo TESORERIA
 */
public record IncomingBatch(String id, String source, List<BatchLine> lines) {
}
