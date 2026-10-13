package com.conciliacion.pagos.application.service;

import com.conciliacion.pagos.application.usecase.NotFoundException;
import com.conciliacion.pagos.domain.model.InvoiceDetail;
import com.conciliacion.pagos.domain.model.InvoiceFilter;
import com.conciliacion.pagos.domain.model.InvoicePage;
import com.conciliacion.pagos.domain.model.InvoicePayment;
import com.conciliacion.pagos.domain.model.InvoiceStatus;
import com.conciliacion.pagos.domain.model.InvoiceView;
import com.conciliacion.pagos.domain.model.LineOutcome;
import com.conciliacion.pagos.domain.repository.InvoiceQueryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Detalle de una factura con sus pagos. */
class InvoiceDetailServiceTest {

    private static final InvoiceView INVOICE = new InvoiceView("FV-1", "900-1", "Proveedor Uno",
        LocalDate.of(2026, 2, 13), LocalDate.of(2026, 3, 15), new BigDecimal("100.00"), new BigDecimal("60.00"),
        InvoiceStatus.PARCIAL);
    private static final InvoicePayment PAYMENT = new InvoicePayment("L-1", 1, "P-1", new BigDecimal("40.00"), null,
        LineOutcome.APLICADO, null, new BigDecimal("100.00"), new BigDecimal("60.00"), InvoiceStatus.PARCIAL,
        BigDecimal.ZERO, "cartera", Instant.EPOCH);

    private final InvoiceDetailService service = new InvoiceDetailService(new InvoiceQueryPort() {
        @Override
        public InvoicePage search(InvoiceFilter filter) {
            return new InvoicePage(List.of(), null);
        }

        @Override
        public Optional<InvoiceView> findByNumber(String number) {
            return INVOICE.number().equals(number) ? Optional.of(INVOICE) : Optional.empty();
        }

        @Override
        public List<InvoicePayment> findPayments(String number) {
            return INVOICE.number().equals(number) ? List.of(PAYMENT) : List.of();
        }
    });

    @Test
    void returnsTheInvoiceWithItsPayments() {
        InvoiceDetail detail = service.getDetail("FV-1");

        assertThat(detail.invoice()).isEqualTo(INVOICE);
        assertThat(detail.payments()).containsExactly(PAYMENT);
    }

    @Test
    void anUnknownInvoiceIsNotFound() {
        assertThatThrownBy(() -> service.getDetail("FV-404"))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("FV-404");
    }
}
