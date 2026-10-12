package com.conciliacion.pagos.application.dto;

import com.conciliacion.pagos.domain.model.BatchChannel;
import com.conciliacion.pagos.domain.model.BatchLine;
import java.time.LocalDate;
import java.util.List;

/**
 * Un lote recien recibido, todavia sin procesar, sin importar por que via llego (RF-16, RF-17).
 *
 * @param id identificador que envia Tesoreria; null o vacio en un CSV sin id (se deriva de la huella, A-06)
 * @param generatedOn fecha de generacion; null si la via no la trae
 */
public record IncomingBatch(String id, String source, BatchChannel channel, LocalDate generatedOn,
                            List<BatchLine> lines) {
}