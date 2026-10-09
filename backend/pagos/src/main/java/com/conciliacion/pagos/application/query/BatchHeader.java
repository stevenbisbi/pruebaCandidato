package com.conciliacion.pagos.application.query;

import java.time.Instant;
import java.time.LocalDate;

/**
 * @param channel JSON o CSV
 * @param contentHash huella del contenido, para distinguir un reenvio de un lote distinto con el mismo id
 */
public record BatchHeader(String id, String source, String channel, LocalDate generatedOn, String contentHash,
                          Instant receivedAt) {
}
