package com.conciliacion.pagos.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;


/**
 * Un lote de pagos ya procesado, con sus totales (RF-16, RF-21).
 *
 * @param id identificador del lote; para un CSV sin id es "CSV-" + parte de la huella
 * @param contentHash huella SHA-256 del contenido: distingue un reenvio de un lote distinto con el mismo id
 (CSV)
 * @param contentHash huella SHA-256 del contenido: distingue un reenvio de un lote distinto con el mismo id (A-07)
 */
public record Batch(String id, String source, BatchChannel channel, LocalDate generatedOn, String contentHash, Instant receivedAt,
                     int appliedLines, int rejectedLines,
                    BigDecimal appliedAmount, BigDecimal rejectedAmount) {

    /** Lote recien recibido, todavia sin procesar: todos los totales en cero. */
    public static Batch received(String id, String source, BatchChannel channel, LocalDate generatedOn,
                                 String contentHash, Instant receivedAt) {
        BigDecimal zero = BigDecimal.ZERO.setScale(Invoice.MONEY_SCALE);
        return new Batch(id, source, channel, generatedOn, contentHash, receivedAt, 0, 0, zero, zero);
    }

    /** El mismo lote con los totales calculados a partir del resultado de sus lineas. */
    public Batch withResults(List<LineResult> results) {
        int applied = 0;
        BigDecimal appliedSum = BigDecimal.ZERO.setScale(Invoice.MONEY_SCALE);
        BigDecimal rejectedSum = BigDecimal.ZERO.setScale(Invoice.MONEY_SCALE);
        for (LineResult result : results) {
            // Una linea con valor ilegible no tiene monto: cuenta como linea, no suma (A-08).
            BigDecimal amount = result.amount() == null ? BigDecimal.ZERO : result.amount();
            if (result.isApplied()) {
                applied++;
                appliedSum = appliedSum.add(amount);
            } else {
                rejectedSum = rejectedSum.add(amount);
            }
        }
        return new Batch(id, source, channel, generatedOn, contentHash, receivedAt,
            applied, results.size() - applied, appliedSum, rejectedSum);
    }

    /** Siempre aplicadas + rechazadas: calculado para que no se pueda descuadrar. */
    public int totalLines() {
        return appliedLines + rejectedLines;
    }
}
