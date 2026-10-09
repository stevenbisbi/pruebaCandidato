package com.conciliacion.pagos.domain;

import com.conciliacion.pagos.domain.model.BatchLine;
import com.conciliacion.pagos.domain.model.Invoice;
import com.conciliacion.pagos.domain.model.InvoiceStatus;
import com.conciliacion.pagos.domain.model.LineOutcome;
import com.conciliacion.pagos.domain.model.LineResult;
import com.conciliacion.pagos.domain.model.RejectionReason;
import com.conciliacion.pagos.domain.service.BatchReconciler;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Casos frontera del Anexo B que dependen solo del dominio. */
class BatchReconcilerTest {

    private final BatchReconciler reconciler = new BatchReconciler();

    @Test
    void cf01_wholePesoPaymentLeavesFortyCentsAndTheInvoicePartial() {
        Invoice invoice = invoice("FV-0042199", "37500483.40", LocalDate.of(2026, 4, 30));

        LineResult result = single(line("CF01-1", "FV-0042199", "37500483", "2026-03-10T10:00:00-05:00"), invoice);

        assertThat(result.outcome()).isEqualTo(LineOutcome.APLICADO);
        assertThat(invoice.balance()).isEqualByComparingTo("0.40");
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.PARCIAL);
    }

    @Test
    void cf02_paymentExceedingTheBalanceByCentsIsRejectedEntirely() {
        Invoice invoice = invoice("FV-0042199", "37500483.40", LocalDate.of(2026, 4, 30));

        LineResult result = single(line("CF02-1", "FV-0042199", "37500484", "2026-03-10T10:00:00-05:00"), invoice);

        assertThat(result.reason()).isEqualTo(RejectionReason.EXCEDE_SALDO);
        assertThat(invoice.balance()).isEqualByComparingTo("37500483.40");
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.PENDIENTE);
        assertThat(result.balanceBefore()).isEqualByComparingTo(result.balanceAfter());
    }

    @Test
    void cf05_paymentAtElevenThirtyPmColombiaOnTheDueDateIsOnTime() {
        Invoice invoice = invoice("FV-0000200", "12500.00", LocalDate.of(2026, 3, 15));

        // 23:30 en Colombia son las 04:30 del 16 en UTC: con la fecha UTC quedaria extemporanea.
        single(line("CF05-1", "FV-0000200", "12500", "2026-03-15T23:30:00-05:00"), invoice);

        assertThat(invoice.status()).isEqualTo(InvoiceStatus.PAGADA);
    }

    @Test
    void paymentOneSecondAfterMidnightColombiaIsLate() {
        Invoice invoice = invoice("FV-0000200", "12500.00", LocalDate.of(2026, 3, 15));

        single(line("L-1", "FV-0000200", "12500", "2026-03-16T05:00:00Z"), invoice);

        assertThat(invoice.status()).isEqualTo(InvoiceStatus.PAGADA_EXTEMPORANEA);
    }

    @Test
    void cf06_firstPaymentAppliesAndTheSecondNoLongerFits() {
        Invoice invoice = invoice("FV-0000100", "100.00", LocalDate.of(2026, 4, 30));

        List<LineResult> results = reconciler.reconcile(List.of(
            line(1, "CF06-A", "FV-0000100", "60", "2026-03-13T08:00:00-05:00"),
            line(2, "CF06-B", "FV-0000100", "70", "2026-03-13T08:01:00-05:00")), map(invoice), Set.of());

        assertThat(results).extracting(LineResult::outcome).containsExactly(LineOutcome.APLICADO, LineOutcome.RECHAZADO);
        assertThat(results.get(1).reason()).isEqualTo(RejectionReason.EXCEDE_SALDO);
        assertThat(results.get(1).balanceBefore()).isEqualByComparingTo("40");
        assertThat(invoice.balance()).isEqualByComparingTo("40");
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.PARCIAL);
    }

    @Test
    void referenceRepeatedInsideTheBatchOrAlreadyAppliedIsRejected() {
        Invoice invoice = invoice("FV-1", "1000.00", LocalDate.of(2026, 4, 30));

        List<LineResult> results = reconciler.reconcile(List.of(
            line(1, "R-1", "FV-1", "100", "2026-03-13T08:00:00-05:00"),
            line(2, "R-1", "FV-1", "100", "2026-03-13T08:00:00-05:00"),
            line(3, "R-OLD", "FV-1", "100", "2026-03-13T08:00:00-05:00")), map(invoice), Set.of("R-OLD"));

        assertThat(results).extracting(LineResult::reason)
            .containsExactly(null, RejectionReason.REFERENCIA_DUPLICADA, RejectionReason.REFERENCIA_DUPLICADA);
        assertThat(invoice.balance()).isEqualByComparingTo("900");
    }

    @Test
    void invalidLinesAreRejectedOneByOneWithTheirReason() {
        Invoice invoice = invoice("FV-1", "1000.00", LocalDate.of(2026, 4, 30));

        List<LineResult> results = reconciler.reconcile(List.of(
            line(1, "A", "FV-1", "100.50", "2026-03-13T08:00:00-05:00"),
            line(2, "B", "FV-1", "-5", "2026-03-13T08:00:00-05:00"),
            line(3, "C", "FV-1", "0", "2026-03-13T08:00:00-05:00"),
            line(4, "D", "FV-1", "100", ""),
            line(5, "E", "FV-1", "100", "2026-03-13T08:00:00"),
            line(6, "F", "FV-404", "100", "2026-03-13T08:00:00-05:00"),
            BatchLine.malformed(7, "Se esperaban 4 columnas"),
            line(8, "G", "FV-1", "100", "2026-03-13T08:00:00-05:00")), map(invoice), Set.of());

        assertThat(results).extracting(LineResult::reason).containsExactly(
            RejectionReason.VALOR_INVALIDO, RejectionReason.VALOR_INVALIDO, RejectionReason.VALOR_INVALIDO,
            RejectionReason.FECHA_INVALIDA, RejectionReason.FECHA_INVALIDA, RejectionReason.FACTURA_INEXISTENTE,
            RejectionReason.FORMATO_INVALIDO, null);
        assertThat(invoice.balance()).isEqualByComparingTo("900");
    }

    @Test
    void linesForDifferentInvoicesGiveTheSameResultInAnyOrder() {
        Invoice a1 = invoice("FV-A", "100.00", LocalDate.of(2026, 4, 30));
        Invoice b1 = invoice("FV-B", "100.00", LocalDate.of(2026, 4, 30));
        Invoice a2 = invoice("FV-A", "100.00", LocalDate.of(2026, 4, 30));
        Invoice b2 = invoice("FV-B", "100.00", LocalDate.of(2026, 4, 30));
        BatchLine x = line(1, "X", "FV-A", "60", "2026-03-13T08:00:00-05:00");
        BatchLine y = line(2, "Y", "FV-B", "150", "2026-03-13T08:00:00-05:00");

        reconciler.reconcile(List.of(x, y), map(a1, b1), Set.of());
        reconciler.reconcile(List.of(y, x), map(a2, b2), Set.of());

        assertThat(a1.balance()).isEqualByComparingTo(a2.balance());
        assertThat(b1.balance()).isEqualByComparingTo(b2.balance());
    }

    private LineResult single(BatchLine line, Invoice invoice) {
        return reconciler.reconcile(List.of(line), map(invoice), Set.of()).getFirst();
    }

    private static Invoice invoice(String number, String total, LocalDate due) {
        BigDecimal amount = new BigDecimal(total);
        return new Invoice(number, amount, amount, due, InvoiceStatus.PENDIENTE);
    }

    private static Map<String, Invoice> map(Invoice... invoices) {
        Map<String, Invoice> map = new HashMap<>();
        for (Invoice i : invoices) {
            map.put(i.number(), i);
        }
        return map;
    }

    private static BatchLine line(String ref, String invoice, String amount, String date) {
        return line(1, ref, invoice, amount, date);
    }

    private static BatchLine line(int n, String ref, String invoice, String amount, String date) {
        return BatchLine.of(n, ref, invoice, amount, date);
    }
}
