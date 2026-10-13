package com.conciliacion.pagos.application.usecase;

import com.conciliacion.pagos.domain.model.Batch;
import com.conciliacion.pagos.domain.model.LineOutcome;
import com.conciliacion.pagos.domain.model.LinePage;
import com.conciliacion.pagos.domain.model.LineResult;
import com.conciliacion.pagos.domain.model.RejectionReason;

import java.util.function.Consumer;

/** Puerto de entrada: consultar un lote ya procesado y su resultado linea por linea (RF-21, RF-22). */
public interface GetBatchResultUseCase {

    /** Tamano maximo de pagina: un lote puede tener 100.000 lineas. */
    int MAX_PAGE_SIZE = 500;

    /** @throws NotFoundException si el lote no existe */
    Batch getBatch(String id);

    /**
     * Una pagina del resultado. Una pagina negativa se toma como la primera, y un tamano fuera de
     * 1..MAX_PAGE_SIZE se ajusta al limite mas cercano.
     *
     * @param outcome filtro opcional por resultado; null para todas
     * @param reason filtro opcional por motivo de rechazo (RF-26); null para todos
     * @throws NotFoundException si el lote no existe
     */
    LinePage getLines(String id, LineOutcome outcome, RejectionReason reason, int page, int size);

    /**
     * Entrega todas las lineas del lote en orden, una a una, para descargarlas (RF-22).
     *
     * @throws NotFoundException si el lote no existe
     */
    void exportLines(String id, Consumer<LineResult> consumer);
}
