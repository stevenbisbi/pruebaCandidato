package com.conciliacion.pagos.application.usecase;

import com.conciliacion.pagos.application.dto.BatchReceipt;
import com.conciliacion.pagos.application.dto.IncomingBatch;

/**
 * Puerto de entrada: recibir y aplicar un lote de pagos (RF-16 a RF-21).
 *
 * Es un solo caso de uso para las dos vias de RF-17: el CSV y el JSON llegan por BatchController.
 */
public interface ReconcileBatchUseCase {
     /**
     * Aplica el lote. Si ya se habia procesado (mismo id y mismo contenido), devuelve el resultado
     * original sin aplicar nada (RF-20, CF-03).
     *
     * @throws BatchConflictException si el id ya existe con otro contenido (A-07)
     */
    BatchReceipt reconcile(IncomingBatch batch);
}
