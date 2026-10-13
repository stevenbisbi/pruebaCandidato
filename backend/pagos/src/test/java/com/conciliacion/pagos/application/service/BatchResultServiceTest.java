package com.conciliacion.pagos.application.service;

import com.conciliacion.pagos.application.usecase.GetBatchResultUseCase;
import com.conciliacion.pagos.application.usecase.NotFoundException;
import com.conciliacion.pagos.domain.model.Batch;
import com.conciliacion.pagos.domain.model.BatchChannel;
import com.conciliacion.pagos.domain.model.LineOutcome;
import com.conciliacion.pagos.domain.model.LinePage;
import com.conciliacion.pagos.domain.model.LineResult;
import com.conciliacion.pagos.domain.model.RejectionReason;
import com.conciliacion.pagos.domain.repository.BatchRepository;
import com.conciliacion.pagos.domain.repository.PaymentLedger;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Consulta de lotes: 404 para un lote inexistente y limites de la paginacion. */
class BatchResultServiceTest {

    private static final Batch BATCH = Batch.received("L-1", "TESORERIA", BatchChannel.CSV, null, "hash", Instant.EPOCH);

    private final FakeLedger ledger = new FakeLedger();
    private final BatchResultService service = new BatchResultService(new FakeBatches(), ledger);

    @Test
    void returnsAnExistingBatch() {
        assertThat(service.getBatch("L-1").id()).isEqualTo("L-1");
    }

    @Test
    void anUnknownBatchIsNotFound() {
        assertThatThrownBy(() -> service.getBatch("NO-EXISTE"))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("NO-EXISTE");
    }

    @Test
    void linesOfAnUnknownBatchAreNotFoundInsteadOfAnEmptyPage() {
        assertThatThrownBy(() -> service.getLines("NO-EXISTE", null, null, 0, 100))
            .isInstanceOf(NotFoundException.class);
        assertThat(ledger.called).isFalse();
    }

    @Test
    void passesTheFiltersToTheLedger() {
        service.getLines("L-1", LineOutcome.RECHAZADO, RejectionReason.EXCEDE_SALDO, 2, 50);

        assertThat(ledger.outcome).isEqualTo(LineOutcome.RECHAZADO);
        assertThat(ledger.reason).isEqualTo(RejectionReason.EXCEDE_SALDO);
        assertThat(ledger.page).isEqualTo(2);
        assertThat(ledger.size).isEqualTo(50);
    }

    @Test
    void adjustsPageAndSizeOutsideTheLimits() {
        service.getLines("L-1", null, null, -3, 100_000);
        assertThat(ledger.page).isZero();
        assertThat(ledger.size).isEqualTo(GetBatchResultUseCase.MAX_PAGE_SIZE);

        service.getLines("L-1", null, null, 0, 0);
        assertThat(ledger.size).isEqualTo(1);
    }

    /** Un solo lote, "L-1". */
    private static class FakeBatches implements BatchRepository {
        @Override
        public boolean insert(Batch batch) {
            return true;
        }

        @Override
        public Batch find(String id) {
            return BATCH.id().equals(id) ? BATCH : null;
        }

        @Override
        public void saveTotals(Batch batch) {
            // No hace falta en estas pruebas.
        }
    }

    /** Anota con que argumentos se pidio la pagina. */
    private static class FakeLedger implements PaymentLedger {
        boolean called;
        LineOutcome outcome;
        RejectionReason reason;
        int page;
        int size;

        @Override
        public Set<String> findAppliedReferences(Collection<String> references) {
            return Set.of();
        }

        @Override
        public void record(String batchId, List<LineResult> results, String user, Instant processedAt) {
            // No hace falta en estas pruebas.
        }

        @Override
        public LinePage findLines(String batchId, LineOutcome outcome, RejectionReason reason, int page, int size) {
            this.called = true;
            this.outcome = outcome;
            this.reason = reason;
            this.page = page;
            this.size = size;
            return new LinePage(List.of(), page, size, 0);
        }
    }
}
