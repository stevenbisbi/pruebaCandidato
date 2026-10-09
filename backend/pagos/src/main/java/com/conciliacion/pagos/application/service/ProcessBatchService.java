package com.conciliacion.pagos.application.service;

import com.conciliacion.pagos.application.port.out.BatchRepository;
import com.conciliacion.pagos.application.port.out.InvoiceRepository;
import com.conciliacion.pagos.application.port.out.PaymentLedger;
import com.conciliacion.pagos.application.port.out.TransactionRunner;
import com.conciliacion.pagos.application.query.BatchHeader;
import com.conciliacion.pagos.application.query.BatchReceipt;
import com.conciliacion.pagos.application.query.BatchTotals;
import com.conciliacion.pagos.application.query.IncomingBatch;
import com.conciliacion.pagos.domain.model.BatchLine;
import com.conciliacion.pagos.domain.model.Invoice;
import com.conciliacion.pagos.domain.model.LineResult;
import com.conciliacion.pagos.domain.service.BatchReconciler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Caso de uso: recibir y aplicar un lote (RF-16 a RF-21).
 *
 * Todo ocurre en una sola transaccion: registro del lote, bloqueo de facturas, aplicacion,
 * auditoria y totales. Si algo falla no queda nada a medias, y un reenvio posterior procesa el lote
 * desde cero.
 */
public class ProcessBatchService {

    private final BatchRepository batches;
    private final InvoiceRepository invoices;
    private final PaymentLedger ledger;
    private final TransactionRunner tx;
    private final Clock clock;
    private final String user;
    private final BatchReconciler reconciler = new BatchReconciler();

    public ProcessBatchService(BatchRepository batches, InvoiceRepository invoices, PaymentLedger ledger,
                               TransactionRunner tx, Clock clock, String user) {
        this.batches = batches;
        this.invoices = invoices;
        this.ledger = ledger;
        this.tx = tx;
        this.clock = clock;
        this.user = user;
    }

    public BatchReceipt process(IncomingBatch batch) {
        if (batch.lines().isEmpty()) {
            throw new IllegalArgumentException("El lote no contiene lineas de pago");
        }
        String hash = contentHash(batch.lines());
        String batchId = isBlank(batch.id()) ? "CSV-" + hash.substring(0, 20) : batch.id().trim();
        return tx.inTransaction(() -> processInTransaction(batchId, hash, batch));
    }

    private BatchReceipt processInTransaction(String batchId, String hash, IncomingBatch batch) {
        Instant now = clock.instant();
        BatchHeader header = new BatchHeader(batchId, batch.source(), batch.channel(), batch.generatedOn(), hash, now);
        if (!batches.register(header)) {
            // RF-20 / CF-03: reenvio. Se devuelve el resultado original sin volver a aplicar nada.
            BatchHeader existing = batches.findHeader(batchId).orElseThrow();
            if (!existing.contentHash().equals(hash)) {
                throw new BatchConflictException(batchId);
            }
            return new BatchReceipt(batches.findSummary(batchId).orElseThrow(), true);
        }

        Set<String> invoiceNumbers = new TreeSet<>();
        Set<String> references = new TreeSet<>();
        for (BatchLine line : batch.lines()) {
            if (!isBlank(line.invoiceNumber())) {
                invoiceNumbers.add(line.invoiceNumber());
            }
            if (!isBlank(line.reference())) {
                references.add(line.reference());
            }
        }
        Map<String, Invoice> locked = invoices.lockByNumbers(invoiceNumbers);
        Set<String> applied = ledger.findAppliedReferences(references);

        List<LineResult> results = reconciler.reconcile(batch.lines(), locked, applied);

        invoices.saveBalances(locked.values().stream().filter(Invoice::isChanged).toList());
        ledger.record(batchId, results, user, now);
        batches.complete(batchId, BatchTotals.of(results));
        return new BatchReceipt(batches.findSummary(batchId).orElseThrow(), false);
    }

    /**
     * Huella del contenido normalizado (sin BOM, sin espacios, sin fin de linea). Asi el mismo lote
     * enviado como CSV desde Windows o desde Linux produce la misma huella.
     */
    static String contentHash(List<BatchLine> lines) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (BatchLine line : lines) {
                String canonical = String.join(";", trim(line.reference()), trim(line.invoiceNumber()),
                    trim(line.rawAmount()), trim(line.rawPaidAt()), Objects.toString(line.formatError(), "")) + "\n";
                digest.update(canonical.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
