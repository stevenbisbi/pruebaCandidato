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

/**
 * Regla de negocio principal: decide, linea por linea, si un pago se aplica o se rechaza.
 *
 * Las lineas se recorren en el orden del lote (RF-18): si dos pagos van a la misma factura, el
 * segundo ve el saldo que dejo el primero. Las facturas distintas no se afectan entre si (RF-19).
 *
 * No usa base de datos: recibe las facturas ya cargadas y devuelve el resultado de cada linea.
 */
public class BatchReconciler {

    private static final int MAX_REFERENCE_LENGTH = 80;
    private static final int MAX_INVOICE_LENGTH = 30;

    /**
     * @param invoices facturas del lote, por numero (las que no existen no estan en el mapa)
     * @param alreadyAppliedReferences referencias que ya se aplicaron en lotes anteriores
     */
    public List<LineResult> reconcile(List<BatchLine> lines, Map<String, Invoice> invoices,
                                      Set<String> alreadyAppliedReferences) {
        Set<String> usedReferences = new HashSet<>(alreadyAppliedReferences);
        List<LineResult> results = new ArrayList<>();
        for (BatchLine line : lines) {
            results.add(processLine(line, invoices, usedReferences));
        }
        return results;
    }

    private LineResult processLine(BatchLine line, Map<String, Invoice> invoices, Set<String> usedReferences) {
        // 1. La linea debe tener sus 4 columnas, referencia y numero de factura.
        if (line.formatError() != null) {
            return rejected(line, RejectionReason.FORMATO_INVALIDO, line.formatError(), null, null, null);
        }
        if (isBlank(line.reference()) || isBlank(line.invoiceNumber())) {
            return rejected(line, RejectionReason.FORMATO_INVALIDO, "Referencia o factura vacia", null, null, null);
        }
        if (line.reference().length() > MAX_REFERENCE_LENGTH || line.invoiceNumber().length() > MAX_INVOICE_LENGTH) {
            return rejected(line, RejectionReason.FORMATO_INVALIDO, "Referencia o factura demasiado larga", null, null, null);
        }

        Invoice invoice = invoices.get(line.invoiceNumber());

        // 2. RF-10: el valor debe ser un entero positivo en pesos.
        BigDecimal amount = parseAmount(line.rawAmount());
        if (amount == null) {
            return rejected(line, RejectionReason.VALOR_INVALIDO, "Valor recibido: '" + line.rawAmount() + "'", null, null, invoice);
        }

        // 3. La fecha debe traer zona horaria, por ejemplo 2026-03-15T23:30:00-05:00.
        OffsetDateTime paidAt = parseDate(line.rawPaidAt());
        if (paidAt == null) {
            return rejected(line, RejectionReason.FECHA_INVALIDA, "Fecha recibida: '" + line.rawPaidAt() + "'", amount, null, invoice);
        }

        // 4. RF-12: una referencia no se aplica dos veces.
        if (usedReferences.contains(line.reference())) {
            return rejected(line, RejectionReason.REFERENCIA_DUPLICADA, null, amount, paidAt, invoice);
        }

        // 5. RF-08: la factura debe existir.
        if (invoice == null) {
            return rejected(line, RejectionReason.FACTURA_INEXISTENTE, null, amount, paidAt, null);
        }

        // 6. RF-09: el pago debe caber completo en el saldo; si no, se rechaza entero.
        if (!invoice.accepts(amount)) {
            return rejected(line, RejectionReason.EXCEDE_SALDO, "Saldo a pagar: " + invoice.payableBalance(), amount, paidAt, invoice);
        }

        // 7. Todo bien: se aplica el pago.
        BigDecimal balanceBefore = invoice.balance();
        invoice.apply(amount, paidAt.toInstant());
        usedReferences.add(line.reference());

        // Si el pago supero el saldo por centavos (porque el saldo se redondea al peso), queda anotado.
        String detail = null;
        if (amount.compareTo(balanceBefore) > 0) {
            detail = "Centavos redondeados: " + amount.subtract(balanceBefore);
        }
        return new LineResult(line.lineNumber(), line.reference(), line.invoiceNumber(), amount, paidAt,
            LineOutcome.APLICADO, null, detail, balanceBefore, invoice.balance(), invoice.status());
    }

    /** Un rechazo no cambia la factura (RF-11): saldo anterior y posterior son iguales. */
    private LineResult rejected(BatchLine line, RejectionReason reason, String detail, BigDecimal amount,
                                OffsetDateTime paidAt, Invoice invoice) {
        BigDecimal balance = null;
        if (invoice != null) {
            balance = invoice.balance();
        }
        return new LineResult(line.lineNumber(), line.reference(), line.invoiceNumber(), amount, paidAt,
            LineOutcome.RECHAZADO, reason, detail, balance, balance, invoice == null ? null : invoice.status());
    }

    /** Devuelve el valor como BigDecimal, o null si no es un entero positivo de hasta 16 digitos. */
    private BigDecimal parseAmount(String raw) {
        if (raw == null || !raw.trim().matches("\\d{1,16}")) {
            return null;
        }
        BigDecimal amount = new BigDecimal(raw.trim()).setScale(Invoice.MONEY_SCALE);
        if (amount.signum() <= 0) {
            return null;
        }
        return amount;
    }

    /** Devuelve la fecha, o null si falta o no trae zona horaria. */
    private OffsetDateTime parseDate(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
