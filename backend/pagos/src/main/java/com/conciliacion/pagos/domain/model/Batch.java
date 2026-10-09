package com.conciliacion.pagos.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un lote de pagos ya procesado, con sus totales (RF-16, RF-21).
 *
 * @param id identificador del lote; para un CSV sin id es "CSV-" + parte de la huella
 * @param contentHash huella SHA-256 del contenido: distingue un reenvio de un lote distinto con el mismo id
 */
public record Batch(String id, String source, String contentHash, Instant receivedAt,
                    int totalLines, int appliedLines, int rejectedLines,
                    BigDecimal appliedAmount, BigDecimal rejectedAmount) {
}
