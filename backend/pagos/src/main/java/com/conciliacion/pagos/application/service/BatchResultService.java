package com.conciliacion.pagos.application.service;

import com.conciliacion.pagos.application.usecase.GetBatchResultUseCase;
import com.conciliacion.pagos.application.usecase.NotFoundException;
import com.conciliacion.pagos.domain.model.Batch;
import com.conciliacion.pagos.domain.model.LineOutcome;
import com.conciliacion.pagos.domain.model.LinePage;
import com.conciliacion.pagos.domain.model.LineResult;
import com.conciliacion.pagos.domain.model.RejectionReason;
import com.conciliacion.pagos.domain.repository.BatchRepository;
import com.conciliacion.pagos.domain.repository.PaymentLedger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;

/** Consulta de lotes ya procesados y de su resultado linea por linea (RF-21, RF-22). */
@Service
public class BatchResultService implements GetBatchResultUseCase {

    private final BatchRepository batches;
    private final PaymentLedger ledger;

    public BatchResultService(BatchRepository batches, PaymentLedger ledger) {
        this.batches = batches;
        this.ledger = ledger;
    }

    @Override
    public Batch getBatch(String id) {
        Batch batch = batches.find(id);
        if (batch == null) {
            throw new NotFoundException("El lote " + id + " no existe");
        }
        return batch;
    }

    @Override
    public LinePage getLines(String id, LineOutcome outcome, RejectionReason reason, int page, int size) {
        // Un lote que no existe responde 404, no una pagina vacia: asi se distingue de un filtro sin resultados.
        getBatch(id);
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        return ledger.findLines(id, outcome, reason, safePage, safeSize);
    }

    /** La transaccion (de solo lectura) es la que permite que la base entregue las lineas en tandas. */
    @Override
    @Transactional(readOnly = true)
    public void exportLines(String id, Consumer<LineResult> consumer) {
        getBatch(id);
        ledger.forEachLine(id, consumer);
    }
}
