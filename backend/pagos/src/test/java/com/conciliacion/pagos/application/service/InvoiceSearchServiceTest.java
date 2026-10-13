package com.conciliacion.pagos.application.service;

import com.conciliacion.pagos.application.usecase.InvalidRequestException;
import com.conciliacion.pagos.application.usecase.SearchInvoicesUseCase;
import com.conciliacion.pagos.domain.model.InvoiceFilter;
import com.conciliacion.pagos.domain.model.InvoicePage;
import com.conciliacion.pagos.domain.model.InvoiceStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Validacion y normalizacion del filtro de la consulta de facturas (RF-23). */
class InvoiceSearchServiceTest {

    private InvoiceFilter received;
    private final InvoiceSearchService service = new InvoiceSearchService(filter -> {
        received = filter;
        return new InvoicePage(List.of(), null);
    });

    @Test
    void rejectsAnInvertedDueDateRange() {
        InvoiceFilter filter = filter(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 3, 1), null, null);

        assertThatThrownBy(() -> service.search(filter)).isInstanceOf(InvalidRequestException.class);
        assertThat(received).isNull();
    }

    @Test
    void rejectsAnInvertedBalanceRange() {
        InvoiceFilter filter = filter(null, null, new BigDecimal("500"), new BigDecimal("100"));

        assertThatThrownBy(() -> service.search(filter)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void acceptsASingleDayRange() {
        LocalDate day = LocalDate.of(2026, 3, 15);

        service.search(filter(day, day, null, null));

        assertThat(received.dueFrom()).isEqualTo(day);
    }

    @Test
    void treatsBlankTextsAsNoFilter() {
        service.search(new InvoiceFilter("  ", null, null, null, null, null, "", 50));

        assertThat(received.supplierNit()).isNull();
        assertThat(received.afterNumber()).isNull();
        assertThat(received.statuses()).isEmpty();
    }

    @Test
    void keepsTheStatusesAndTrimsTheNit() {
        service.search(new InvoiceFilter(" 900123 ", Set.of(InvoiceStatus.PARCIAL), null, null, null, null, null, 50));

        assertThat(received.supplierNit()).isEqualTo("900123");
        assertThat(received.statuses()).containsExactly(InvoiceStatus.PARCIAL);
    }

    @Test
    void adjustsThePageSizeToTheLimits() {
        service.search(new InvoiceFilter(null, null, null, null, null, null, null, 10_000));
        assertThat(received.size()).isEqualTo(SearchInvoicesUseCase.MAX_PAGE_SIZE);

        service.search(new InvoiceFilter(null, null, null, null, null, null, null, 0));
        assertThat(received.size()).isEqualTo(1);
    }

    private InvoiceFilter filter(LocalDate from, LocalDate to, BigDecimal min, BigDecimal max) {
        return new InvoiceFilter(null, Set.of(), from, to, min, max, null, 50);
    }
}
