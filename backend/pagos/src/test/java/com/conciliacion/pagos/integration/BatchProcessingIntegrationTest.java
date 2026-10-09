package com.conciliacion.pagos.integration;

import com.conciliacion.pagos.application.dto.BatchReceipt;
import com.conciliacion.pagos.application.dto.IncomingBatch;
import com.conciliacion.pagos.application.usecase.ProcessBatchUseCase;
import com.conciliacion.pagos.domain.model.BatchLine;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CF-03 y CF-04 contra PostgreSQL real (Testcontainers): el bloqueo de filas y la espera sobre
 * una llave en conflicto no se pueden demostrar con una base en memoria.
 * Se omiten automaticamente si no hay Docker disponible.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration")
class BatchProcessingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    ProcessBatchUseCase processBatch;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE payment_line_result, payment_batch, invoice, supplier CASCADE");
        jdbc.update("INSERT INTO supplier (nit, name) VALUES ('900-1', 'Proveedor Uno')");
        insertInvoice("FV-0000300", "899000.00");
        insertInvoice("FV-0000400", "500000.00");
    }

    @Test
    void cf03_resendingTheSameBatchReturnsTheOriginalResultWithoutApplyingTwice() {
        IncomingBatch batch = csvBatch(
            BatchLine.of(1, "CF03-1", "FV-0000300", "400000", "2026-03-11T09:15:00-05:00"),
            BatchLine.of(2, "CF03-2", "FV-0000300", "250000", "2026-03-11T09:16:00-05:00"));

        BatchReceipt first = processBatch.process(batch);
        BatchReceipt second = processBatch.process(batch);

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.batch().id()).isEqualTo(first.batch().id());
        assertThat(balance("FV-0000300")).isEqualByComparingTo("249000");
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
            int applied = fa.get().batch().appliedLines() + fb.get().batch().appliedLines();

            assertThat(applied).isEqualTo(1);
            assertThat(balance("FV-0000400")).isIn(new BigDecimal("200000.00"), new BigDecimal("220000.00"));
        } finally {
            pool.shutdownNow();
        }
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
        return new IncomingBatch(null, "TESORERIA", List.of(lines));
    }

    private void insertInvoice(String number, String total) {
        jdbc.update("""
            INSERT INTO invoice (number, supplier_nit, issue_date, due_date, total_amount, balance, status)
            VALUES (?, '900-1', DATE '2026-01-01', DATE '2026-05-31', ?::numeric, ?::numeric, 'PENDIENTE')
            """, number, total, total);
    }

    private BigDecimal balance(String number) {
        return jdbc.queryForObject("SELECT balance FROM invoice WHERE number = ?", BigDecimal.class, number);
    }
}
