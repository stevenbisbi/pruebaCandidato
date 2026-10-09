package com.conciliacion.pagos.application.usecase;

import com.conciliacion.pagos.application.dto.BatchReceipt;
import com.conciliacion.pagos.application.dto.IncomingBatch;

/** Puerto de entrada: recibir y aplicar un lote de pagos (RF-16 a RF-21). */
public interface ProcessBatchUseCase {

    /**
     * Aplica el lote. Si el lote ya habia sido procesado (mismo id y mismo contenido), devuelve el
     * resultado original sin aplicar nada (RF-20).
     *
     * @throws BatchConflictException si el id ya existe con un contenido distinto
     */
    BatchReceipt process(IncomingBatch batch);
}
