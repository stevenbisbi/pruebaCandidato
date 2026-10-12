package com.conciliacion.pagos.application.service;

import com.conciliacion.pagos.application.dto.BatchReceipt;
import com.conciliacion.pagos.application.dto.IncomingBatch;

import com.conciliacion.pagos.application.usecase.BatchConflictException;
import com.conciliacion.pagos.application.usecase.ReconcileBatchUseCase;

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
 * Recibe un lote y aplica sus pagos (RF-16 a RF-21). No tiene reglas de negocio propias: coordina
 * al dominio (BatchReconciler) y a los puertos de salida, en una sola transaccion.
 */
@Service
public class ReconcileBatchService implements ReconcileBatchUseCase {

    private final BatchRepository batches;
    private final InvoiceRepository invoices;
    private final PaymentLedger ledger;
    private final String user;
    private final BatchReconciler reconciler = new BatchReconciler();

    public ReconcileBatchService(BatchRepository batches, InvoiceRepository invoices, PaymentLedger ledger,
                                 @Value("${app.user}") String user) {
        this.batches = batches;
        this.invoices = invoices;
        this.ledger = ledger;
        this.user = user;
    }

    @Override
    @Transactional
    public BatchReceipt reconcile(IncomingBatch incoming) {
        // 1. Identificar el lote. Un CSV sin id usa su huella (A-06).
        String hash = contentHash(incoming.lines());
        String id = (incoming.id() == null || incoming.id().isBlank())
            ? "CSV-" + hash.substring(0, 20)
            : incoming.id();
        Instant now = Instant.now();

        // 2. Registrarlo. Si ya existia es un reenvio (RF-20, CF-03) o un conflicto (A-07).
        Batch received = Batch.received(id, incoming.source(), incoming.channel(), incoming.generatedOn(), hash, now);
        if (!batches.insert(received)) {
            Batch existing = batches.find(id);
            if (!existing.contentHash().equals(hash)) {
                throw new BatchConflictException(id);
            }
            return new BatchReceipt(existing, true);
        }

        // 3. Bloquear las facturas del lote (CF-04) y ver que referencias ya se aplicaron (RF-12).
        List<String> invoiceNumbers = new ArrayList<>();
        List<String> references = new ArrayList<>();
        for (BatchLine line : incoming.lines()) {
            if (line.invoiceNumber() != null) {invoiceNumbers.add(line.invoiceNumber());}
            if (line.reference() != null) {references.add(line.reference());}
        }
        Map<String, Invoice> locked = invoices.lockByNumbers(invoiceNumbers);
        Set<String> applied = ledger.findAppliedReferences(references);

        // 4. Aplicar los pagos linea por linea: la regla de negocio esta en el dominio.
        List<LineResult> results = reconciler.reconcile(incoming.lines(), locked, applied);

        // 5. Guardar saldos, resultado de cada linea (auditoria, RF-15) y totales.
        invoices.saveBalances(locked.values().stream().filter(Invoice::isChanged).toList());
        ledger.record(id, results, user, now);
        Batch processed = received.withResults(results);
        batches.saveTotals(processed);
        return new BatchReceipt(processed, false);
    }

    /** Huella SHA-256 del contenido: el mismo lote produce siempre la misma huella. */
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
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(text.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 viene obligatoriamente en toda JVM: esto no deberia pasar nunca.
            throw new IllegalStateException(e);
        }
    }
}