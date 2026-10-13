package com.conciliacion.pagos.integration;

import com.conciliacion.pagos.application.dto.BatchReceipt;
import com.conciliacion.pagos.application.dto.IncomingBatch;
import com.conciliacion.pagos.application.usecase.BatchConflictException;
import com.conciliacion.pagos.application.usecase.ReconcileBatchUseCase;
import com.conciliacion.pagos.domain.model.BatchChannel;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Procesamiento de lotes contra PostgreSQL real (Testcontainers). El bloqueo de filas, la espera
 * sobre una llave en conflicto y la transaccion no se pueden demostrar con una base en memoria.
 *
 * Usa solo las migraciones de esquema (sin la carga de 500.000 facturas) y siembra en cada prueba
 * las facturas que necesita. Sin Docker estas pruebas fallan: no se saltan en silencio.
 */
@Testcontainers
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration")
class BatchProcessingIntegrationTest {

    private static final LocalDate DUE_DATE = LocalDate.of(2026, 3, 15);
    private static final String ON_TIME = "2026-03-15T23:30:00-05:00";
    private static final String LATE = "2026-03-16T00:00:00-05:00";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    ReconcileBatchUseCase reconcileBatch;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE payment_line_result, payment_batch, invoice, supplier CASCADE");
        jdbc.update("INSERT INTO supplier (nit, name) VALUES ('900-1', 'Proveedor Uno')");
    }

    // --- CF-03: reenvios ---

    @Test
    void cf03_resendingTheSameBatchReturnsTheOriginalResultWithoutApplyingTwice() {
        insertInvoice("FV-1", "100000.00");
        IncomingBatch batch = batch("TES-CF03", line(1, "CF03-1", "FV-1", "40000", ON_TIME));

        BatchReceipt first = reconcileBatch.reconcile(batch);
        BatchReceipt second = reconcileBatch.reconcile(batch);

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.batch().appliedLines()).isEqualTo(1);
        assertThat(balance("FV-1")).isEqualByComparingTo("60000");
        assertThat(lineCount()).isEqualTo(1);
    }

    @Test
    void cf03_simultaneousResendsApplyTheBatchOnlyOnce() throws Exception {
        insertInvoice("FV-1", "100000.00");
        IncomingBatch batch = batch("TES-CF03-PAR", line(1, "PAR-1", "FV-1", "40000", ON_TIME));

        // Cuatro reenvios del mismo lote al mismo tiempo: el INSERT ... ON CONFLICT hace esperar a los
        // demas hasta que el primero confirma, y entonces ven el lote ya registrado.
        List<BatchReceipt> receipts = runConcurrently(4, i -> reconcileBatch.reconcile(batch));

        assertThat(receipts).filteredOn(r -> !r.replayed()).hasSize(1);
        assertThat(receipts).filteredOn(BatchReceipt::replayed).hasSize(3);
        assertThat(balance("FV-1")).isEqualByComparingTo("60000");
        assertThat(lineCount()).isEqualTo(1);
    }

    @Test
    void a07_theSameIdWithDifferentContentIsAConflictAndAppliesNothing() {
        insertInvoice("FV-1", "100000.00");
        reconcileBatch.reconcile(batch("TES-A07", line(1, "A07-1", "FV-1", "40000", ON_TIME)));

        IncomingBatch changed = batch("TES-A07", line(1, "A07-1", "FV-1", "50000", ON_TIME));

        assertThatThrownBy(() -> reconcileBatch.reconcile(changed)).isInstanceOf(BatchConflictException.class);
        assertThat(balance("FV-1")).isEqualByComparingTo("60000");
    }

    // --- CF-04: concurrencia entre lotes distintos ---

    @Test
    void cf04_concurrentBatchesNeverApplyMoreThanTheInvoiceBalance() throws Exception {
        // 30 facturas de 100. El lote A paga 60 a cada una; el B paga 70, recorriendolas en orden
        // inverso: si las facturas no se bloquearan siempre en el mismo orden, los dos lotes se
        // esperarian mutuamente (deadlock).
        int invoices = 30;
        List<BatchLine> linesA = new ArrayList<>();
        List<BatchLine> linesB = new ArrayList<>();
        for (int i = 1; i <= invoices; i++) {
            insertInvoice(invoiceNumber(i), "100.00");
            linesA.add(line(i, "A-" + i, invoiceNumber(i), "60", ON_TIME));
            linesB.add(line(i, "B-" + i, invoiceNumber(invoices + 1 - i), "70", ON_TIME));
        }
        IncomingBatch batchA = new IncomingBatch("TES-CF04-A", "TESORERIA", BatchChannel.JSON, null, linesA);
        IncomingBatch batchB = new IncomingBatch("TES-CF04-B", "TESORERIA", BatchChannel.JSON, null, linesB);

        List<BatchReceipt> receipts = runConcurrently(2, i -> reconcileBatch.reconcile(i == 0 ? batchA : batchB));

        // Los dos lotes terminan (no hubo deadlock) y entre ambos aplican exactamente un pago por factura.
        assertThat(receipts.get(0).batch().appliedLines() + receipts.get(1).batch().appliedLines()).isEqualTo(invoices);
        for (int i = 1; i <= invoices; i++) {
            BigDecimal applied = jdbc.queryForObject(
                "SELECT coalesce(sum(amount), 0) FROM payment_line_result WHERE invoice_number = ? AND outcome = 'APLICADO'",
                BigDecimal.class, invoiceNumber(i));
            assertThat(applied).isLessThanOrEqualTo(new BigDecimal("100"));
            assertThat(balance(invoiceNumber(i)).add(applied)).isEqualByComparingTo("100");
        }
    }

    @Test
    void rf12_aReferenceInTwoConcurrentBatchesIsAppliedOnlyOnce() throws Exception {
        // Facturas distintas: los lotes no se bloquean entre si y la referencia compartida la frena
        // el indice unico (o, si un lote alcanza a ver la confirmacion del otro, el dominio).
        int rounds = 10;
        for (int round = 0; round < rounds; round++) {
            cleanDatabase();
            insertInvoice("FV-A", "100.00");
            insertInvoice("FV-B", "100.00");
            IncomingBatch batchA = batch("TES-RF12-A", line(1, "COMPARTIDA", "FV-A", "10", ON_TIME));
            IncomingBatch batchB = batch("TES-RF12-B", line(1, "COMPARTIDA", "FV-B", "10", ON_TIME));

            runConcurrently(2, i -> {
                try {
                    return reconcileBatch.reconcile(i == 0 ? batchA : batchB);
                } catch (org.springframework.dao.DuplicateKeyException e) {
                    return null; // El perdedor de la carrera: su lote entero se deshizo.
                }
            });

            Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM payment_line_result WHERE reference = 'COMPARTIDA' AND outcome = 'APLICADO'",
                Integer.class);
            assertThat(applied).as("ronda %d", round).isEqualTo(1);
            assertThat(balance("FV-A").add(balance("FV-B"))).as("ronda %d", round).isEqualByComparingTo("190");
        }
    }

    // --- Lo que se guarda en la base ---

    @Test
    void cf01_theRoundingAdjustmentIsStoredInTheAudit() {
        insertInvoice("FV-1", "37500483.40");

        reconcileBatch.reconcile(batch("TES-CF01", line(1, "CF01-1", "FV-1", "37500483", ON_TIME)));

        assertThat(balance("FV-1")).isEqualByComparingTo("0");
        assertThat(status("FV-1")).isEqualTo("PAGADA");
        assertThat(jdbc.queryForObject("SELECT rounding_adjustment FROM payment_line_result", BigDecimal.class))
            .isEqualByComparingTo("0.40");
        assertThat(jdbc.queryForObject("SELECT processed_by FROM payment_line_result", String.class))
            .isEqualTo("cartera");
    }

    @Test
    void a04_aLatePaymentInOneBatchIsRememberedByTheNext() {
        insertInvoice("FV-1", "100.00");

        reconcileBatch.reconcile(batch("TES-A04-1", line(1, "A04-1", "FV-1", "40", LATE)));
        reconcileBatch.reconcile(batch("TES-A04-2", line(1, "A04-2", "FV-1", "60", ON_TIME)));

        assertThat(status("FV-1")).isEqualTo("PAGADA_EXTEMPORANEA");
    }

    @Test
    void rf12_aReferenceAppliedInOneBatchIsRejectedInTheNext() {
        insertInvoice("FV-1", "100.00");
        reconcileBatch.reconcile(batch("TES-RF12-1", line(1, "RF12", "FV-1", "10", ON_TIME)));

        BatchReceipt second = reconcileBatch.reconcile(batch("TES-RF12-2", line(1, "RF12", "FV-1", "10", ON_TIME)));

        assertThat(second.batch().rejectedLines()).isEqualTo(1);
        assertThat(balance("FV-1")).isEqualByComparingTo("90");
    }

    @Test
    void aBatchWithAnOverlongReferenceIsStoredInsteadOfFailingWhole() {
        insertInvoice("FV-1", "100.00");

        BatchReceipt receipt = reconcileBatch.reconcile(batch("TES-LARGO",
            line(1, "R".repeat(200), "FV-1", "10", ON_TIME),
            line(2, "OK-1", "FV-1", "10", ON_TIME)));

        assertThat(receipt.batch().appliedLines()).isEqualTo(1);
        assertThat(receipt.batch().rejectedLines()).isEqualTo(1);
        assertThat(lineCount()).isEqualTo(2);
    }

    // --- Utilidades ---

    private void insertInvoice(String number, String amount) {
        jdbc.update("""
            INSERT INTO invoice (number, supplier_nit, issue_date, due_date, total_amount, balance, status)
            VALUES (?, '900-1', ?, ?, ?, ?, 'PENDIENTE')
            """, number, DUE_DATE.minusDays(30), DUE_DATE, new BigDecimal(amount), new BigDecimal(amount));
    }

    private static String invoiceNumber(int i) {
        return "FV-%04d".formatted(i);
    }

    private static BatchLine line(int number, String reference, String invoice, String amount, String paidAt) {
        return BatchLine.of(number, reference, invoice, amount, paidAt);
    }

    private static IncomingBatch batch(String id, BatchLine... lines) {
        return new IncomingBatch(id, "TESORERIA", BatchChannel.JSON, null, List.of(lines));
    }

    private BigDecimal balance(String number) {
        return jdbc.queryForObject("SELECT balance FROM invoice WHERE number = ?", BigDecimal.class, number);
    }

    private String status(String number) {
        return jdbc.queryForObject("SELECT status FROM invoice WHERE number = ?", String.class, number);
    }

    private int lineCount() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM payment_line_result", Integer.class);
        return count == null ? 0 : count;
    }

    /** Ejecuta la tarea en varios hilos que arrancan a la vez, y devuelve los resultados en orden. */
    private static <T> List<T> runConcurrently(int threads, IndexedTask<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                Callable<T> callable = () -> {
                    start.await();
                    return task.run(index);
                };
                futures.add(pool.submit(callable));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface IndexedTask<T> {
        T run(int index);
    }
}
