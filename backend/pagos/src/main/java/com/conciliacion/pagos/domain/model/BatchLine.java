package com.conciliacion.pagos.domain.model;

/**
 * Linea de un lote tal como llega, sin interpretar. Los valores se conservan como texto para
 * que una linea mal formada se rechace sola (RF-21) en lugar de tumbar el lote entero, que es
 * justamente el defecto del importador heredado.
 *
 * @param lineNumber posicion 1-based dentro del lote (en CSV, sin contar el encabezado)
 * @param formatError motivo de formato detectado por el lector (columnas faltantes), o null
 */
public record BatchLine(int lineNumber, String reference, String invoiceNumber, String rawAmount,
                        String rawPaidAt, String formatError) {

    public static BatchLine of(int lineNumber, String reference, String invoiceNumber, String rawAmount,
                               String rawPaidAt) {
        return new BatchLine(lineNumber, reference, invoiceNumber, rawAmount, rawPaidAt, null);
    }

    public static BatchLine malformed(int lineNumber, String detail) {
        return new BatchLine(lineNumber, null, null, null, null, detail);
    }
}
