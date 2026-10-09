package com.conciliacion.pagos.application.dto;

import com.conciliacion.pagos.domain.model.Batch;

/**
 * Lo que devuelve el caso de uso al procesar un lote.
 *
 * @param replayed true si el lote ya se habia procesado antes y se devuelve el resultado original
 */
public record BatchReceipt(Batch batch, boolean replayed) {
}
