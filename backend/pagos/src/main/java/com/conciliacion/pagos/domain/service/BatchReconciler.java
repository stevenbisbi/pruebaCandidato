package com.conciliacion.pagos.domain.service;

import com.conciliacion.pagos.domain.model.BatchLine;
import com.conciliacion.pagos.domain.model.Invoice;
import com.conciliacion.pagos.domain.model.LineOutcome;
import com.conciliacion.pagos.domain.model.LineResult;
import com.conciliacion.pagos.domain.model.RejectionReason;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Aplica las lineas de un lote contra las facturas, en el orden del lote (RF-18).
 *
 * No toca persistencia: recibe las facturas ya bloqueadas y las referencias ya aplicadas, y
 * devuelve el resultado linea por linea. Las facturas recibidas quedan mutadas con el saldo final.
 *
 * Sobre RF-19: las lineas de facturas distintas son independientes entre si, de modo que su orden
 * relativo no cambia el resultado. Para lineas de una misma factura manda RF-18 (ver DECISIONS.md).
 */
public final class BatchReconciler {

    /** RF-10: Tesoreria envia pesos enteros. Se admite solo digitos, sin signo ni decimales. */
    private static final Pattern WHOLE_PESOS = Pattern.compile("\\d{1,16}");
    private static final int MAX_REFERENCE_LENGTH = 80;
    private static final int MAX_INVOICE_LENGTH = 30;

    public List<LineResult> reconcile(List<BatchLine> lines, Map<String, Invoice> invoices,
                                      Set<String> alreadyAppliedReferences) {
        Set<String> usedReferences = new HashSet<>(alreadyAppliedReferences);
        List<LineResult> results = new ArrayList<>(lines.size());
        for (BatchLine line : lines) {
            results.add(process(line, invoices, usedReferences));
        }
        return results;
    }

    private LineResult process(BatchLine line, Map<String, Invoice> invoices, Set<String> usedReferences) {
        if (line.formatError() != null || isBlank(line.reference()) || isBlank(line.invoiceNumber())) {
            String detail = line.formatError() != null ? line.formatError() : "Referencia o factura vacia";
            return rejected(line, null, null, RejectionReason.FORMATO_INVALIDO, detail, null);
        }
        if (line.reference().length() > MAX_REFERENCE_LENGTH || line.invoiceNumber().length() > MAX_INVOICE_LENGTH) {
            return rejected(line, null, null, RejectionReason.FORMATO_INVALIDO,
                "Referencia o numero de factura demasiado largos", null);
        }
        BigDecimal amount = parseAmount(line.rawAmount());
        if (amount == null) {
            return rejected(line, null, null, RejectionReason.VALOR_INVALIDO,
                "Valor recibido: '" + nullToEmpty(line.rawAmount()) + "'", invoices.get(line.invoiceNumber()));
        }
        OffsetDateTime paidAt = parseDate(line.rawPaidAt());
        if (paidAt == null) {
            return rejected(line, amount, null, RejectionReason.FECHA_INVALIDA,
                "Fecha recibida: '" + nullToEmpty(line.rawPaidAt()) + "'", invoices.get(line.invoiceNumber()));
        }
        Invoice invoice = invoices.get(line.invoiceNumber());
        if (usedReferences.contains(line.reference())) {
            return rejected(line, amount, paidAt, RejectionReason.REFERENCIA_DUPLICADA, null, invoice);
        }
        if (invoice == null) {
            return rejected(line, amount, paidAt, RejectionReason.FACTURA_INEXISTENTE, null, null);
        }
        if (!invoice.accepts(amount)) {
            return rejected(line, amount, paidAt, RejectionReason.EXCEDE_SALDO,
                "Saldo disponible: " + invoice.balance().toPlainString(), invoice);
        }
        BigDecimal before = invoice.balance();
        invoice.apply(amount, paidAt.toInstant());
        usedReferences.add(line.reference());
        return new LineResult(line.lineNumber(), line.reference(), line.invoiceNumber(), amount, paidAt,
            LineOutcome.APLICADO, null, null, before, invoice.balance(), invoice.status());
    }

    private static LineResult rejected(BatchLine line, BigDecimal amount, OffsetDateTime paidAt,
                                       RejectionReason reason, String detail, Invoice invoice) {
        BigDecimal balance = invoice == null ? null : invoice.balance();
        return new LineResult(line.lineNumber(), line.reference(), line.invoiceNumber(), amount, paidAt,
            LineOutcome.RECHAZADO, reason, detail, balance, balance, invoice == null ? null : invoice.status());
    }

    private static BigDecimal parseAmount(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (!WHOLE_PESOS.matcher(value).matches()) {
            return null;
        }
        BigDecimal amount = new BigDecimal(value).setScale(Invoice.MONEY_SCALE);
        return amount.signum() > 0 ? amount : null;
    }

    /** Se exige offset explicito: una fecha sin zona es ambigua justo en la frontera de RF-13. */
    private static OffsetDateTime parseDate(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
