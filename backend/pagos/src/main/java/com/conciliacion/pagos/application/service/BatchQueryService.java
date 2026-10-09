package com.conciliacion.pagos.application.service;

import com.conciliacion.pagos.application.port.out.BatchRepository;
import com.conciliacion.pagos.application.port.out.PaymentLedger;
import com.conciliacion.pagos.application.query.BatchSummary;
import com.conciliacion.pagos.application.query.LinePage;
import com.conciliacion.pagos.domain.model.LineOutcome;
import com.conciliacion.pagos.domain.model.LineResult;
import com.conciliacion.pagos.domain.model.RejectionReason;

import java.util.function.Consumer;

/** Consulta y descarga del resultado de un lote ya procesado (RF-22). */
public class BatchQueryService {

    private static final int MAX_PAGE_SIZE = 500;

    private final BatchRepository batches;
    private final PaymentLedger ledger;

    public BatchQueryService(BatchRepository batches, PaymentLedger ledger) {
        this.batches = batches;
        this.ledger = ledger;
    }

    public BatchSummary summary(String batchId) {
        return batches.findSummary(batchId).orElseThrow(() -> new NotFoundException("No existe el lote " + batchId));
    }

    public LinePage lines(String batchId, LineOutcome outcome, RejectionReason reason, int page, int size) {
        summary(batchId);
        int boundedSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        return ledger.findLines(batchId, outcome, reason, Math.max(page, 0), boundedSize);
    }

    public void export(String batchId, Consumer<LineResult> consumer) {
        summary(batchId);
        ledger.streamLines(batchId, consumer);
    }
}
