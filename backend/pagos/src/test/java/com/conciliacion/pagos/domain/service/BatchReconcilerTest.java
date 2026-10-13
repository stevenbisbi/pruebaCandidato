package com.conciliacion.pagos.domain.service;

import com.conciliacion.pagos.domain.model.BatchLine;
import com.conciliacion.pagos.domain.model.Invoice;
import com.conciliacion.pagos.domain.model.InvoiceStatus;
import com.conciliacion.pagos.domain.model.LineOutcome;
import com.conciliacion.pagos.domain.model.LineResult;
import com.conciliacion.pagos.domain.model.RejectionReason;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas de aplicacion de un lote, linea por linea (RF-07 a RF-12, RF-18, RF-21) y los casos
 * frontera del Anexo B que no necesitan base de datos.
 */
class BatchReconcilerTest {

    private static final String ON_TIME = "2026-03-15T23:30:00-05:00";
    private static final String LATE = "2026-03-16T00:00:00-05:00";

    private final BatchReconciler reconciler = new BatchReconciler();
    private final Map<String, Invoice> invoices = new HashMap<>();

    // --- Casos frontera ---

    @Test
    void cf01_appliesThePaymentAndRecordsTheForgivenCents() {
        addInvoice("FV-1", "37500483.40");

        LineResult result = single(line(1, "P-1", "FV-1", "37500483", ON_TIME));

        assertThat(result.outcome()).isEqualTo(LineOutcome.APLICADO);
        assertThat(result.statusAfter()).isEqualTo(InvoiceStatus.PAGADA);
        assertThat(result.balanceBefore()).isEqualByComparingTo("37500483.40");
        assertThat(result.balanceAfter()).isEqualByComparingTo("0");
        assertThat(result.roundingAdjustment()).isEqualByComparingTo("0.40");
    }

    @Test
    void cf02_rejectsAPaymentAboveThePayableBalanceAndLeavesTheBalanceUntouched() {
        addInvoice("FV-1", "37500483.40");

        LineResult result = single(line(1, "P-1", "FV-1", "37500484", ON_TIME));

        assertThat(result.outcome()).isEqualTo(LineOutcome.RECHAZADO);
        assertThat(result.reason()).isEqualTo(RejectionReason.EXCEDE_SALDO);
        // RF-11: saldo anterior y posterior iguales.
        assertThat(result.balanceBefore()).isEqualByComparingTo("37500483.40");
        assertThat(result.balanceAfter()).isEqualByComparingTo("37500483.40");
        assertThat(result.roundingAdjustment()).isNull();
        assertThat(invoices.get("FV-1").isChanged()).isFalse();
    }

    @Test
    void cf05_aPaymentAt2330OnTheDueDateIsOnTime() {
        // Debe pasar igual con la JVM en UTC (-Duser.timezone=UTC): la zona la fija DueDatePolicy.
        addInvoice("FV-1", "100.00");

        LineResult result = single(line(1, "P-1", "FV-1", "100", ON_TIME));

        assertThat(result.statusAfter()).isEqualTo(InvoiceStatus.PAGADA);
    }

    @Test
    void aPaymentAtMidnightAfterTheDueDateIsLate() {
        addInvoice("FV-1", "100.00");

        LineResult result = single(line(1, "P-1", "FV-1", "100", LATE));

        assertThat(result.statusAfter()).isEqualTo(InvoiceStatus.PAGADA_EXTEMPORANEA);
    }

    @Test
    void cf06_appliesPaymentsInBatchOrderAndRejectsTheOneThatNoLongerFits() {
        addInvoice("FV-1", "100.00");

        List<LineResult> results = reconcile(
            line(1, "A", "FV-1", "60", ON_TIME),
            line(2, "B", "FV-1", "70", ON_TIME));

        assertThat(results.get(0).outcome()).isEqualTo(LineOutcome.APLICADO);
        assertThat(results.get(0).balanceAfter()).isEqualByComparingTo("40");
        assertThat(results.get(1).outcome()).isEqualTo(LineOutcome.RECHAZADO);
        assertThat(results.get(1).reason()).isEqualTo(RejectionReason.EXCEDE_SALDO);
        assertThat(results.get(1).balanceBefore()).isEqualByComparingTo("40");
    }

    @Test
    void cf06_inReverseOrderTheOtherPaymentIsRejected() {
        // A-01: manda RF-18. El resultado de una factura depende del orden de sus propias lineas.
        addInvoice("FV-1", "100.00");

        List<LineResult> results = reconcile(
            line(1, "B", "FV-1", "70", ON_TIME),
            line(2, "A", "FV-1", "60", ON_TIME));

        assertThat(results).extracting(LineResult::outcome)
            .containsExactly(LineOutcome.APLICADO, LineOutcome.RECHAZADO);
    }

    @Test
    void linesOfDifferentInvoicesDoNotAffectEachOther() {
        // A-01: RF-19 se cumple entre facturas distintas.
        addInvoice("FV-1", "100.00");
        addInvoice("FV-2", "50.00");

        List<LineResult> results = reconcile(
            line(1, "A", "FV-2", "50", ON_TIME),
            line(2, "B", "FV-1", "100", ON_TIME));

        assertThat(results).extracting(LineResult::statusAfter)
            .containsExactly(InvoiceStatus.PAGADA, InvoiceStatus.PAGADA);
    }

    // --- Valor del pago (RF-10, A-02) ---

    @Test
    void roundsAPaymentWithDecimalsToTheNearestPesoAndKeepsTheReceivedValue() {
        addInvoice("FV-1", "200.00");

        LineResult result = single(line(1, "P-1", "FV-1", "100.6", ON_TIME));

        assertThat(result.outcome()).isEqualTo(LineOutcome.APLICADO);
        assertThat(result.amount()).isEqualByComparingTo("101");
        assertThat(result.detail()).isEqualTo("Valor recibido: 100.6");
    }

    @Test
    void aWholePaymentHasNoDetail() {
        addInvoice("FV-1", "200.00");

        LineResult result = single(line(1, "P-1", "FV-1", "100", ON_TIME));

        assertThat(result.detail()).isNull();
    }

    @Test
    void rejectsAPaymentThatRoundsToZero() {
        addInvoice("FV-1", "200.00");

        LineResult result = single(line(1, "P-1", "FV-1", "0.4", ON_TIME));

        assertThat(result.reason()).isEqualTo(RejectionReason.VALOR_INVALIDO);
    }

    @Test
    void rejectsAmountsThatAreNotPositiveNumbersWithADecimalPoint() {
        addInvoice("FV-1", "200.00");

        List<LineResult> results = reconcile(
            line(1, "P-1", "FV-1", "100,5", ON_TIME),
            line(2, "P-2", "FV-1", "-5", ON_TIME),
            line(3, "P-3", "FV-1", "abc", ON_TIME),
            line(4, "P-4", "FV-1", "", ON_TIME),
            line(5, "P-5", "FV-1", "0", ON_TIME));

        assertThat(results).extracting(LineResult::reason).containsOnly(RejectionReason.VALOR_INVALIDO);
        assertThat(invoices.get("FV-1").isChanged()).isFalse();
    }

    // --- Fecha, formato y factura ---

    @Test
    void rejectsADateWithoutOffsetOrEmpty() {
        addInvoice("FV-1", "200.00");

        List<LineResult> results = reconcile(
            line(1, "P-1", "FV-1", "100", "2026-03-15T10:00:00"),
            line(2, "P-2", "FV-1", "100", ""));

        assertThat(results).extracting(LineResult::reason).containsOnly(RejectionReason.FECHA_INVALIDA);
    }

    @Test
    void rejectsALineMarkedAsMalformedByTheReader() {
        addInvoice("FV-1", "200.00");

        LineResult result = single(BatchLine.malformed(1, "Se esperaban 4 columnas y llegaron 3"));

        assertThat(result.reason()).isEqualTo(RejectionReason.FORMATO_INVALIDO);
        assertThat(result.detail()).isEqualTo("Se esperaban 4 columnas y llegaron 3");
    }

    @Test
    void rejectsTooLongTextsAndTruncatesThemToFitTheAuditColumns() {
        // Si se guardaran completos, la base rechazaria la fila y se desharia el lote entero.
        String longReference = "R".repeat(200);
        String longInvoice = "F".repeat(100);

        LineResult result = single(line(1, longReference, longInvoice, "100", ON_TIME));

        assertThat(result.reason()).isEqualTo(RejectionReason.FORMATO_INVALIDO);
        assertThat(result.reference()).hasSize(80);
        assertThat(result.invoiceNumber()).hasSize(30);
    }

    @Test
    void truncatesTheDetailOfAnAbsurdAmountToFitItsColumn() {
        addInvoice("FV-1", "200.00");

        LineResult result = single(line(1, "P-1", "FV-1", "x".repeat(500), ON_TIME));

        assertThat(result.reason()).isEqualTo(RejectionReason.VALOR_INVALIDO);
        assertThat(result.detail()).hasSize(300);
    }

    @Test
    void rejectsAPaymentToAnInvoiceThatDoesNotExist() {
        LineResult result = single(line(1, "P-1", "FV-404", "100", ON_TIME));

        assertThat(result.reason()).isEqualTo(RejectionReason.FACTURA_INEXISTENTE);
        assertThat(result.balanceBefore()).isNull();
    }

    // --- Referencias (RF-12, A-05) ---

    @Test
    void rejectsAReferenceRepeatedInTheSameBatch() {
        addInvoice("FV-1", "200.00");

        List<LineResult> results = reconcile(
            line(1, "P-1", "FV-1", "50", ON_TIME),
            line(2, "P-1", "FV-1", "50", ON_TIME));

        assertThat(results.get(1).reason()).isEqualTo(RejectionReason.REFERENCIA_DUPLICADA);
        assertThat(invoices.get("FV-1").balance()).isEqualByComparingTo("150");
    }

    @Test
    void rejectsAReferenceAppliedInAPreviousBatch() {
        addInvoice("FV-1", "200.00");

        List<LineResult> results = reconciler.reconcile(
            List.of(line(1, "P-1", "FV-1", "50", ON_TIME)), invoices, Set.of("P-1"));

        assertThat(results.get(0).reason()).isEqualTo(RejectionReason.REFERENCIA_DUPLICADA);
    }

    @Test
    void a05_aRejectedReferenceCanBeAppliedLater() {
        addInvoice("FV-1", "100.00");

        List<LineResult> results = reconcile(
            line(1, "P-1", "FV-1", "500", ON_TIME),
            line(2, "P-1", "FV-1", "100", ON_TIME));

        assertThat(results).extracting(LineResult::outcome)
            .containsExactly(LineOutcome.RECHAZADO, LineOutcome.APLICADO);
    }

    // --- Utilidades ---

    private void addInvoice(String number, String balance) {
        invoices.put(number, new Invoice(number, new BigDecimal(balance), new BigDecimal(balance),
            LocalDate.of(2026, 3, 15), InvoiceStatus.PENDIENTE, false));
    }

    private BatchLine line(int number, String reference, String invoice, String amount, String paidAt) {
        return BatchLine.of(number, reference, invoice, amount, paidAt);
    }

    private List<LineResult> reconcile(BatchLine... lines) {
        return reconciler.reconcile(List.of(lines), invoices, Set.of());
    }

    private LineResult single(BatchLine line) {
        return reconcile(line).get(0);
    }
}
