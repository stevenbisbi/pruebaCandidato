package com.conciliacion.pagos.application.service;

import com.conciliacion.pagos.application.usecase.InvalidRequestException;
import com.conciliacion.pagos.domain.model.ConciliationDashboard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Validacion del rango del tablero (RF-24). */
class ConciliationServiceTest {

    private int calls;
    private final ConciliationService service = new ConciliationService((from, to) -> {
        calls++;
        return new ConciliationDashboard(from, to, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0,
            BigDecimal.ZERO, List.of());
    });

    @Test
    void rejectsAnInvertedRangeWithoutQuerying() {
        assertThatThrownBy(() -> service.getDashboard(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 3, 1)))
            .isInstanceOf(InvalidRequestException.class);
        assertThat(calls).isZero();
    }

    @Test
    void acceptsOpenRanges() {
        service.getDashboard(null, LocalDate.of(2026, 3, 31));
        service.getDashboard(LocalDate.of(2026, 3, 1), null);
        service.getDashboard(null, null);

        assertThat(calls).isEqualTo(3);
    }
}
