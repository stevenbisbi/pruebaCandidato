package com.conciliacion.pagos.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas de la factura: redondeo al peso (A-02), pago tardio (A-04) y sus invariantes. */
class InvoiceTest {

    private static final LocalDate DUE_DATE = LocalDate.of(2026, 3, 15);
    private static final Instant ON_TIME = OffsetDateTime.parse("2026-03-15T23:30:00-05:00").toInstant();
    /** Primer instante en que la factura ya esta vencida: 00:00 del dia siguiente en Colombia. */
    private static final Instant LATE = OffsetDateTime.parse("2026-03-16T00:00:00-05:00").toInstant();

    // --- Redondeo al peso (A-02) ---

    @Test
    void cf01_paymentWithoutCentsSettlesTheInvoiceAndForgivesTheResidue() {
        // 1. Preparar: una factura con saldo 37.500.483,40
        Invoice invoice = invoice("37500483.40");

        // 2. Ejecutar: llega un pago de 37.500.483
        BigDecimal adjustment = invoice.apply(new BigDecimal("37500483.00"), ON_TIME);

        // 3. Verificar: queda PAGADA, en cero, con 0,40 condonados
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.PAGADA);
        assertThat(invoice.balance()).isEqualByComparingTo("0");
        assertThat(adjustment).isEqualByComparingTo("0.40");
    }

    @Test
    void cf02_doesNotAcceptAPaymentAboveThePayableBalance() {
        Invoice invoice = invoice("37500483.40");

        // El saldo a pagar es 37.500.483: un peso mas ya excede el saldo (RF-09).
        assertThat(invoice.payableBalance()).isEqualByComparingTo("37500483");
        assertThat(invoice.accepts(new BigDecimal("37500484"))).isFalse();
        assertThat(invoice.accepts(new BigDecimal("37500483"))).isTrue();
    }

    @Test
    void cf02_applyingAPaymentAboveThePayableBalanceFailsAndLeavesTheBalanceUntouched() {
        Invoice invoice = invoice("37500483.40");

        assertThatThrownBy(() -> invoice.apply(new BigDecimal("37500484"), ON_TIME))
            .isInstanceOf(IllegalStateException.class);
        // RF-11: un pago que no se aplica no modifica la factura.
        assertThat(invoice.balance()).isEqualByComparingTo("37500483.40");
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.PENDIENTE);
        assertThat(invoice.isChanged()).isFalse();
    }

    @Test
    void halfPesoRoundsUpSoTheInvoiceIsSettledWithAnOverpayment() {
        // 100,50 se cobra como 101. Al pagarlo el saldo quedaria en -0,50, que HALF_UP llevaria a -1:
        // la factura debe quedar saldada igual, con el sobrepago registrado.
        Invoice invoice = invoice("100.50");

        BigDecimal adjustment = invoice.apply(new BigDecimal("101"), ON_TIME);

        assertThat(invoice.status()).isEqualTo(InvoiceStatus.PAGADA);
        assertThat(invoice.balance()).isEqualByComparingTo("0");
        assertThat(adjustment).isEqualByComparingTo("-0.50");
    }

    @Test
    void halfPesoLeftAfterAPaymentKeepsTheInvoicePartial() {
        Invoice invoice = invoice("100.50");

        BigDecimal adjustment = invoice.apply(new BigDecimal("100"), ON_TIME);

        // 0,50 redondea a 1 peso: todavia hay algo que cobrar.
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.PARCIAL);
        assertThat(invoice.balance()).isEqualByComparingTo("0.50");
        assertThat(invoice.payableBalance()).isEqualByComparingTo("1");
        assertThat(adjustment).isEqualByComparingTo("0");
    }

    @Test
    void exactPaymentSettlesWithoutAdjustment() {
        Invoice invoice = invoice("100.00");

        BigDecimal adjustment = invoice.apply(new BigDecimal("100"), ON_TIME);

        assertThat(invoice.status()).isEqualTo(InvoiceStatus.PAGADA);
        assertThat(adjustment).isEqualByComparingTo("0");
    }

    // --- Estados y pagos tardios (RF-03, RF-13, A-04) ---

    @Test
    void partialPaymentLeavesTheInvoicePartial() {
        Invoice invoice = invoice("100.00");

        invoice.apply(new BigDecimal("40"), ON_TIME);

        assertThat(invoice.status()).isEqualTo(InvoiceStatus.PARCIAL);
        assertThat(invoice.balance()).isEqualByComparingTo("60");
        assertThat(invoice.isChanged()).isTrue();
    }

    @Test
    void cf05_paymentAt2330ColombiaTimeOnTheDueDateIsOnTime() {
        Invoice invoice = invoice("100.00");

        invoice.apply(new BigDecimal("100"), ON_TIME);

        assertThat(invoice.status()).isEqualTo(InvoiceStatus.PAGADA);
        assertThat(invoice.hadLatePayment()).isFalse();
    }

    @Test
    void paymentAtMidnightAfterTheDueDateIsLate() {
        Invoice invoice = invoice("100.00");

        invoice.apply(new BigDecimal("100"), LATE);

        assertThat(invoice.status()).isEqualTo(InvoiceStatus.PAGADA_EXTEMPORANEA);
    }

    @Test
    void a04_aLatePartialPaymentMakesTheInvoiceLateEvenIfTheClosingPaymentIsOnTime() {
        Invoice invoice = invoice("100.00");

        invoice.apply(new BigDecimal("40"), LATE);
        invoice.apply(new BigDecimal("60"), ON_TIME);

        assertThat(invoice.status()).isEqualTo(InvoiceStatus.PAGADA_EXTEMPORANEA);
    }

    @Test
    void a04_aLatePaymentLoadedFromThePastIsKept() {
        // La factura llega de la base con un pago tardio anterior: el cierre a tiempo no lo borra.
        Invoice invoice = new Invoice("FV-1", new BigDecimal("100"), new BigDecimal("60"), DUE_DATE,
            InvoiceStatus.PARCIAL, true);

        invoice.apply(new BigDecimal("60"), ON_TIME);

        assertThat(invoice.status()).isEqualTo(InvoiceStatus.PAGADA_EXTEMPORANEA);
    }

    // --- Invariantes del constructor y de apply ---

    @Test
    void rejectsABalanceGreaterThanTheTotal() {
        assertThatThrownBy(() -> new Invoice("FV-1", new BigDecimal("100"), new BigDecimal("200"),
                DUE_DATE, InvoiceStatus.PENDIENTE, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("debe estar entre 0 y el total");
    }

    @Test
    void rejectsANegativeBalance() {
        assertThatThrownBy(() -> new Invoice("FV-1", new BigDecimal("100"), new BigDecimal("-1"),
                DUE_DATE, InvoiceStatus.PENDIENTE, false))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANonPositiveTotal() {
        assertThatThrownBy(() -> new Invoice("FV-1", BigDecimal.ZERO, BigDecimal.ZERO,
                DUE_DATE, InvoiceStatus.PENDIENTE, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("debe ser positivo");
    }

    @Test
    void rejectsANonPositivePayment() {
        Invoice invoice = invoice("100.00");

        assertThatThrownBy(() -> invoice.apply(BigDecimal.ZERO, ON_TIME))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /** Factura nueva, sin pagos: el saldo es igual al total (RF-02). */
    private Invoice invoice(String amount) {
        return new Invoice("FV-1", new BigDecimal(amount), new BigDecimal(amount), DUE_DATE,
            InvoiceStatus.PENDIENTE, false);
    }
}
