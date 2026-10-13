package com.conciliacion.pagos.integration;

import com.conciliacion.pagos.application.dto.IncomingBatch;
import com.conciliacion.pagos.application.usecase.GetConciliationStateUseCase;
import com.conciliacion.pagos.application.usecase.ReconcileBatchUseCase;
import com.conciliacion.pagos.domain.model.BatchChannel;
import com.conciliacion.pagos.domain.model.BatchLine;
import com.conciliacion.pagos.domain.model.ConciliationDashboard;
import com.conciliacion.pagos.domain.model.SupplierBalance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Cifras del tablero (RF-24, A-08) contra PostgreSQL real. */
@Testcontainers
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration")
class DashboardIntegrationTest {

    private static final LocalDate MARCH_1 = LocalDate.of(2026, 3, 1);
    private static final LocalDate MARCH_31 = LocalDate.of(2026, 3, 31);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    GetConciliationStateUseCase conciliation;

    @Autowired
    ReconcileBatchUseCase reconcileBatch;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE payment_line_result, payment_batch, invoice, supplier CASCADE");
        jdbc.update("INSERT INTO supplier (nit, name) VALUES ('900-1', 'Proveedor Uno'), ('900-2', 'Proveedor Dos')");
        // Marzo: FV-1 y FV-2 del proveedor 1, FV-3 del proveedor 2. Abril: FV-4, fuera del rango.
        insertInvoice("FV-1", "900-1", LocalDate.of(2026, 3, 10), "1000.00");
        insertInvoice("FV-2", "900-1", LocalDate.of(2026, 3, 20), "500.00");
        insertInvoice("FV-3", "900-2", LocalDate.of(2026, 3, 25), "2000.00");
        insertInvoice("FV-4", "900-2", LocalDate.of(2026, 4, 15), "9000.00");

        reconcileBatch.reconcile(new IncomingBatch("TES-TABLERO", "TESORERIA", BatchChannel.JSON, null, List.of(
            BatchLine.of(1, "P-1", "FV-1", "400", "2026-03-05T10:00:00-05:00"),   // aplicado
            BatchLine.of(2, "P-2", "FV-3", "2000", "2026-03-05T10:00:00-05:00"),  // aplicado, la salda
            BatchLine.of(3, "P-3", "FV-2", "900", "2026-03-05T10:00:00-05:00"),   // rechazado: excede
            BatchLine.of(4, "P-4", "FV-2", "abc", "2026-03-05T10:00:00-05:00"),   // rechazado sin monto
            BatchLine.of(5, "P-5", "FV-404", "70", "2026-03-05T10:00:00-05:00"),  // factura inexistente
            BatchLine.of(6, "P-6", "FV-4", "1000", "2026-03-05T10:00:00-05:00")))); // aplicado, en abril
    }

    @Test
    void summarizesOnlyTheInvoicesDueInTheRange() {
        ConciliationDashboard d = conciliation.getDashboard(MARCH_1, MARCH_31);

        assertThat(d.invoiceCount()).isEqualTo(3);
        assertThat(d.totalInvoiced()).isEqualByComparingTo("3500");
        assertThat(d.totalPending()).isEqualByComparingTo("1100");   // 600 de FV-1 + 500 de FV-2
        assertThat(d.appliedCount()).isEqualTo(2);
        assertThat(d.totalApplied()).isEqualByComparingTo("2400");
        // P-3 y P-4 van a FV-2; P-4 no tiene monto legible y no suma. P-5 no tiene factura y no entra.
        assertThat(d.rejectedCount()).isEqualTo(2);
        assertThat(d.totalRejected()).isEqualByComparingTo("900");
    }

    @Test
    void ranksSuppliersByPendingBalanceAndLeavesOutTheOnesWithNothingPending() {
        ConciliationDashboard d = conciliation.getDashboard(MARCH_1, MARCH_31);

        // El proveedor 2 saldo su unica factura de marzo: no aparece.
        assertThat(d.topSuppliers()).extracting(SupplierBalance::nit).containsExactly("900-1");
        assertThat(d.topSuppliers().get(0).pendingBalance()).isEqualByComparingTo("1100");
        assertThat(d.topSuppliers().get(0).invoiceCount()).isEqualTo(2);
    }

    @Test
    void withoutARangeCoversEveryInvoice() {
        ConciliationDashboard d = conciliation.getDashboard(null, null);

        assertThat(d.invoiceCount()).isEqualTo(4);
        assertThat(d.totalApplied()).isEqualByComparingTo("3400");
        assertThat(d.topSuppliers()).extracting(SupplierBalance::nit).containsExactly("900-2", "900-1");
    }

    private void insertInvoice(String number, String nit, LocalDate dueDate, String amount) {
        jdbc.update("""
            INSERT INTO invoice (number, supplier_nit, issue_date, due_date, total_amount, balance, status)
            VALUES (?, ?, ?, ?, ?, ?, 'PENDIENTE')
            """, number, nit, dueDate.minusDays(30), dueDate, new BigDecimal(amount), new BigDecimal(amount));
    }
}
