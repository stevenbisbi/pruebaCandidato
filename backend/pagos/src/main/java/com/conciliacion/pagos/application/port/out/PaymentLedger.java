package com.conciliacion.pagos.application.port.out;

import com.conciliacion.pagos.application.query.LinePage;
import com.conciliacion.pagos.domain.model.LineOutcome;
import com.conciliacion.pagos.domain.model.LineResult;
import com.conciliacion.pagos.domain.model.RejectionReason;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Libro de aplicacion de pagos: resultado por linea y auditoria (RF-15, RF-21, RF-22). */
public interface PaymentLedger {

    Set<String> findAppliedReferences(Collection<String> references);

    void record(String batchId, List<LineResult> results, String user, Instant processedAt);

    LinePage findLines(String batchId, LineOutcome outcome, RejectionReason reason, int page, int size);

    void streamLines(String batchId, Consumer<LineResult> consumer);

    List<LineResult> findByInvoice(String invoiceNumber);
}
