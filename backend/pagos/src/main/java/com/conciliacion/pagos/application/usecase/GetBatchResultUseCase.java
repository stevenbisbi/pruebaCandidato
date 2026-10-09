package com.conciliacion.pagos.application.usecase;

import com.conciliacion.pagos.domain.model.Batch;
import com.conciliacion.pagos.domain.model.LinePage;

/** Puerto de entrada: consultar el resultado de un lote ya procesado (RF-21, RF-22). */
public interface GetBatchResultUseCase {

    /** Lanza NotFoundException si el lote no existe. */
    Batch getBatch(String batchId);

    /** Lanza NotFoundException si el lote no existe. */
    LinePage getLines(String batchId, int page, int size);
}
