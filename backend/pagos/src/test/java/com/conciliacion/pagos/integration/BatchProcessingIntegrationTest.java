package com.conciliacion.pagos.integration;

import com.conciliacion.pagos.application.query.BatchReceipt;
import com.conciliacion.pagos.application.query.IncomingBatch;
import com.conciliacion.pagos.application.query.InvoiceFilter;
import com.conciliacion.pagos.application.query.InvoicePage;
import com.conciliacion.pagos.application.query.InvoiceView;
import com.conciliacion.pagos.application.service.BatchConflictException;
import com.conciliacion.pagos.application.service.InvoiceQueryService;
import com.conciliacion.pagos.application.service.ProcessBatchService;
import com.conciliacion.pagos.domain.model.BatchLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas contra PostgreSQL real (Testcontainers). Cubren lo que una base en memoria no puede
 * demostrar: bloqueos de fila, el indice unico parcial y la espera sobre una llave en conflicto.
 * Se omiten automaticamente si no hay Docker disponible.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration")
class BatchProcessingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    ProcessBatchService processBatch;

    @Autowired
    InvoiceQueryService invoiceQuery;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE payment_line_result, payment_batch, invoice, supplier CASCADE");
        jdbc.update("INSERT INTO supplier (nit, name) VALUES ('900-1', 'Proveedor Uno')");
        insertInvoice("FV-0000300", "899000.00", "2026-05-31");
        insertInvoice("FV-0000400", "500000.00", "2026-05-31");
    }

    @Test
    void cf03_resendingTheSameBatchReturnsTheOriginalResultWithoutApplyingTwice() {
        IncomingBatch batch = csvBatch(
            BatchLine.of(1, "CF03-1", "FV-0000300", "400000", "2026-03-11T09:15:00-05:00"),
            BatchLine.of(2, "CF03-2", "FV-0000300", "250000", "2026-03-11T09:16:00-05:00"));

        BatchReceipt first = processBatch.process(batch);
        BigDecimal balanceAfterFirst = balance("FV-0000300");
        BatchReceipt second = processBatch.process(batch);

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.summary().header().id()).isEqualTo(first.summary().header().id());
        assertThat(balance("FV-0000300")).isEqualByComparingTo(balanceAfterFirst).isEqualByComparingTo("249000");
    }

    @Test
    void sameReferencesUnderANewBatchIdAreRejectedAsDuplicates() {
        processBatch.process(new IncomingBatch("L-1", "TESORERIA", "JSON", null,
            List.of(BatchLine.of(1, "R-1", "FV-0000300", "1000", "2026-03-11T09:15:00-05:00"))));
        BatchReceipt again = processBatch.process(new IncomingBatch("L-2", "TESORERIA", "JSON", null,
            List.of(BatchLine.of(1, "R-1", "FV-0000300", "1000", "2026-03-11T09:15:00-05:00"))));

        assertThat(again.summary().rejectionsByReason()).containsEntry("REFERENCIA_DUPLICADA", 1L);
        assertThat(balance("FV-0000300")).isEqualByComparingTo("898000");
    }

    @Test
    void sameBatchIdWithDifferentContentIsAConflict() {
        processBatch.process(new IncomingBatch("L-1", "TESORERIA", "JSON", null,
            List.of(BatchLine.of(1, "R-1", "FV-0000300", "1000", "2026-03-11T09:15:00-05:00"))));

        assertThatThrownBy(() -> processBatch.process(new IncomingBatch("L-1", "TESORERIA", "JSON", null,
            List.of(BatchLine.of(1, "R-1", "FV-0000300", "2000", "2026-03-11T09:15:00-05:00")))))
            .isInstanceOf(BatchConflictException.class);
    }

    @Test
    void cf04_concurrentBatchesNeverApplyMoreThanTheOriginalBalance() throws Exception {
        // 300.000 + 280.000 > 500.000: exactamente uno de los dos debe aplicarse.
        IncomingBatch a = csvBatch(BatchLine.of(1, "CF04-A", "FV-0000400", "300000", "2026-03-12T11:00:00-05:00"));
        IncomingBatch b = csvBatch(BatchLine.of(1, "CF04-B", "FV-0000400", "280000", "2026-03-12T11:00:00-05:00"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            CompletableFuture<BatchReceipt> fa = CompletableFuture.supplyAsync(() -> await(start, a), pool);
            CompletableFuture<BatchReceipt> fb = CompletableFuture.supplyAsync(() -> await(start, b), pool);
            start.countDown();
            int applied = fa.get().summary().totals().appliedLines() + fb.get().summary().totals().appliedLines();

            assertThat(applied).isEqualTo(1);
            assertThat(balance("FV-0000400")).isIn(new BigDecimal("200000.00"), new BigDecimal("220000.00"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentResendOfTheSameBatchAppliesOnlyOnce() throws Exception {
        IncomingBatch batch = csvBatch(BatchLine.of(1, "R-9", "FV-0000400", "1000", "2026-03-12T11:00:00-05:00"));
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<CompletableFuture<BatchReceipt>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> await(start, batch), pool));
            }
            start.countDown();
            long fresh = 0;
            for (CompletableFuture<BatchReceipt> f : futures) {
                fresh += f.get().replayed() ? 0 : 1;
            }

            assertThat(fresh).isEqualTo(1);
            assertThat(balance("FV-0000400")).isEqualByComparingTo("499000");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void cf08_keysetPaginationNeitherRepeatsNorSkipsWhilePaymentsAreApplied() {
        for (int i = 1; i <= 25; i++) {
            insertInvoice("FV-9%06d".formatted(i), "1000.00", "2026-0%d-1%d".formatted(1 + i % 3, i % 10));
        }
        Set<String> seen = new HashSet<>();
        List<String> order = new ArrayList<>();
        InvoiceView last = null;
        boolean hasNext = true;
        int page = 0;
        while (hasNext) {
            InvoicePage p = invoiceQuery.search(new InvoiceFilter(null, null, null, null, null, null,
                last == null ? null : last.dueDate(), last == null ? null : last.number(), 4));
            p.items().forEach(v -> order.add(v.number()));
            seen.addAll(p.items().stream().map(InvoiceView::number).toList());
            // Mientras se navega se aplica un pago que cambia saldo y estado de una factura ya vista.
            if (page++ == 1) {
                processBatch.process(csvBatch(
                    BatchLine.of(1, "NAV-1", p.items().getFirst().number(), "10", "2026-01-01T10:00:00-05:00")));
            }
            last = p.items().isEmpty() ? null : p.items().getLast();
            hasNext = p.hasNext();
        }

        assertThat(order).hasSize(27).doesNotHaveDuplicates();
        assertThat(seen).hasSize(27);
    }

    @Test
    void appliedPaymentsAreProtectedByTheDatabaseEvenIfTheCodeMissedTheDuplicate() {
        processBatch.process(new IncomingBatch("L-1", "TESORERIA", "JSON", null,
            List.of(BatchLine.of(1, "R-1", "FV-0000300", "1000", "2026-03-11T09:15:00-05:00"))));

        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO payment_line_result (batch_id, line_number, reference, outcome, processed_by, processed_at)
            VALUES ('L-1', 99, 'R-1', 'APLICADO', 'test', now())
            """)).isInstanceOf(DuplicateKeyException.class);
    }

    private BatchReceipt await(CountDownLatch start, IncomingBatch batch) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return processBatch.process(batch);
    }

    private static IncomingBatch csvBatch(BatchLine... lines) {
        return new IncomingBatch(null, "TESORERIA", "CSV", null, List.of(lines));
    }

    private void insertInvoice(String number, String total, String due) {
        jdbc.update("""
            INSERT INTO invoice (number, supplier_nit, issue_date, due_date, total_amount, balance, status)
            VALUES (?, '900-1', DATE '2026-01-01', ?::date, ?::numeric, ?::numeric, 'PENDIENTE')
            """, number, due, total, total);
    }

    private BigDecimal balance(String number) {
        return jdbc.queryForObject("SELECT balance FROM invoice WHERE number = ?", BigDecimal.class, number);
    }
}
