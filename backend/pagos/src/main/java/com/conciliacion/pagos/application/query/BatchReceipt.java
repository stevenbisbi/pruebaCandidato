package com.conciliacion.pagos.application.query;

/** @param replayed true si el lote ya habia sido procesado y se devuelve el resultado original */
public record BatchReceipt(BatchSummary summary, boolean replayed) {
}
