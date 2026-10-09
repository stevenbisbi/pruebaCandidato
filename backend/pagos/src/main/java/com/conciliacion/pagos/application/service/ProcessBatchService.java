package com.conciliacion.pagos.application.service;

import com.conciliacion.pagos.application.dto.BatchReceipt;
import com.conciliacion.pagos.application.dto.IncomingBatch;
import com.conciliacion.pagos.application.usecase.BatchConflictException;
import com.conciliacion.pagos.application.usecase.ProcessBatchUseCase;
import com.conciliacion.pagos.domain.model.Batch;
import com.conciliacion.pagos.domain.model.BatchLine;
import com.conciliacion.pagos.domain.model.Invoice;
import com.conciliacion.pagos.domain.model.LineResult;
import com.conciliacion.pagos.domain.repository.BatchRepository;
import com.conciliacion.pagos.domain.repository.InvoiceRepository;
import com.conciliacion.pagos.domain.repository.PaymentLedger;
import com.conciliacion.pagos.domain.service.BatchReconciler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Caso de uso principal: recibir un lote y aplicar sus pagos (RF-16 a RF-21).
 *
 * Todo el metodo corre en una sola transaccion (@Transactional): si algo falla a mitad de camino
 * no queda nada aplicado, y el reenvio de Tesoreria procesa el lote desde cero.
 */
@Service
public class ProcessBatchService implements ProcessBatchUseCase {

    private final BatchRepository batches;
    private final InvoiceRepository invoices;
    private final PaymentLedger ledger;
    private final String user;
    private final BatchReconciler reconciler = new BatchReconciler();

    public ProcessBatchService(BatchRepository batches, InvoiceRepository invoices, PaymentLedger ledger,
                               @Value("${app.user}") String user) {
        this.batches = batches;
        this.invoices = invoices;
        this.ledger = ledger;
        this.user = user;
    }

    @Override
    @Transactional
    public BatchReceipt process(IncomingBatch incoming) {
        List<BatchLine> lines = incoming.lines();
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("El lote no contiene lineas de pago");
        }

        // 1. Identificar el lote. El CSV no trae id: se usa la huella de su contenido.
        String hash = contentHash(lines);
        String batchId = incoming.id();
        if (batchId == null || batchId.isBlank()) {
            batchId = "CSV-" + hash.substring(0, 20);
        }

        // 2. Registrar el lote. Si ya existia, es un reenvio (RF-20, CF-03).
        Instant now = Instant.now();
        boolean isNew = batches.insert(batchId, incoming.source(), hash, now);
        if (!isNew) {
            Batch existing = batches.find(batchId);
            if (!existing.contentHash().equals(hash)) {
                throw new BatchConflictException(batchId);
            }
            return new BatchReceipt(existing, true);
        }

        // 3. Cargar y bloquear las facturas del lote, y ver que referencias ya se aplicaron antes.
        List<String> invoiceNumbers = new ArrayList<>();
        List<String> references = new ArrayList<>();
        for (BatchLine line : lines) {
            if (line.invoiceNumber() != null) {
                invoiceNumbers.add(line.invoiceNumber());
            }
            if (line.reference() != null) {
                references.add(line.reference());
            }
        }
        Map<String, Invoice> lockedInvoices = invoices.lockByNumbers(invoiceNumbers);
        Set<String> appliedReferences = ledger.findAppliedReferences(references);

        // 4. Aplicar los pagos linea por linea (la regla de negocio esta en BatchReconciler).
        List<LineResult> results = reconciler.reconcile(lines, lockedInvoices, appliedReferences);

        // 5. Guardar saldos, resultado por linea y totales del lote.
        List<Invoice> changedInvoices = new ArrayList<>();
        for (Invoice invoice : lockedInvoices.values()) {
            if (invoice.isChanged()) {
                changedInvoices.add(invoice);
            }
        }
        invoices.saveBalances(changedInvoices);
        ledger.record(batchId, results, user, now);

        Batch batch = withTotals(batchId, incoming.source(), hash, now, results);
        batches.saveTotals(batch);
        return new BatchReceipt(batch, false);
    }

    private Batch withTotals(String batchId, String source, String hash, Instant receivedAt, List<LineResult> results) {
        int applied = 0;
        BigDecimal appliedAmount = new BigDecimal("0.00");
        BigDecimal rejectedAmount = new BigDecimal("0.00");
        for (LineResult result : results) {
            BigDecimal amount = result.amount() == null ? BigDecimal.ZERO : result.amount();
            if (result.isApplied()) {
                applied++;
                appliedAmount = appliedAmount.add(amount);
            } else {
                rejectedAmount = rejectedAmount.add(amount);
            }
        }
        int rejected = results.size() - applied;
        return new Batch(batchId, source, hash, receivedAt, results.size(), applied, rejected,
            appliedAmount, rejectedAmount);
    }

    /** Huella SHA-256 del contenido: el mismo archivo siempre da la misma huella. */
    static String contentHash(List<BatchLine> lines) {
        StringBuilder text = new StringBuilder();
        for (BatchLine line : lines) {
            text.append(line.reference()).append(';')
                .append(line.invoiceNumber()).append(';')
                .append(line.rawAmount()).append(';')
                .append(line.rawPaidAt()).append(';')
                .append(line.formatError()).append('\n');
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
