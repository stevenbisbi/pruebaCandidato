package com.conciliacion.pagos.application.service;

import com.conciliacion.pagos.application.usecase.GetBatchResultUseCase;
import com.conciliacion.pagos.application.usecase.NotFoundException;
import com.conciliacion.pagos.domain.model.Batch;
import com.conciliacion.pagos.domain.model.LinePage;
import com.conciliacion.pagos.domain.repository.BatchRepository;
import com.conciliacion.pagos.domain.repository.PaymentLedger;
import org.springframework.stereotype.Service;

/** Consultar el resultado de un lote ya procesado (RF-21, RF-22). */
@Service
public class BatchQueryService implements GetBatchResultUseCase {

    private static final int MAX_PAGE_SIZE = 500;

    private final BatchRepository batches;
    private final PaymentLedger ledger;

    public BatchQueryService(BatchRepository batches, PaymentLedger ledger) {
        this.batches = batches;
        this.ledger = ledger;
    }

    @Override
    public Batch getBatch(String batchId) {
        Batch batch = batches.find(batchId);
        if (batch == null) {
            throw new NotFoundException("No existe el lote " + batchId);
        }
        return batch;
    }

    @Override
    public LinePage getLines(String batchId, int page, int size) {
        getBatch(batchId);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return ledger.findLines(batchId, safePage, safeSize);
    }
}
