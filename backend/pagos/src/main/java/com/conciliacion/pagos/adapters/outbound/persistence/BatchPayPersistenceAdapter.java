package com.conciliacion.pagos.adapters.outbound.persistence;

import org.springframework.stereotype.Repository;

import com.conciliacion.pagos.domain.model.Batch;
import com.conciliacion.pagos.domain.model.BatchChannel;
import com.conciliacion.pagos.domain.model.InvoiceStatus;
import com.conciliacion.pagos.domain.model.LineOutcome;
import com.conciliacion.pagos.domain.model.LinePage;
import com.conciliacion.pagos.domain.model.LineResult;
import com.conciliacion.pagos.domain.model.RejectionReason;

import com.conciliacion.pagos.domain.repository.BatchRepository;
import com.conciliacion.pagos.domain.repository.PaymentLedger;

import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Lotes y resultado por linea en PostgreSQL. */
@Repository
public class BatchPayPersistenceAdapter implements BatchRepository, PaymentLedger {

    private static final int INSERT_BATCH_SIZE = 1_000;
    private final JdbcTemplate jdbc;

    public BatchPayPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // --- BatchRepository ---

    /**
     * ON CONFLICT DO NOTHING: si el id ya existe no inserta y devuelve 0 filas. Si otra
     * transaccion esta insertando el mismo id, PostgreSQL hace esperar a esta hasta que aquella
     * termine: asi dos reenvios simultaneos no se procesan dos veces (CF-03).
     */
    @Override
    public boolean insert(Batch b) {
        int rows = jdbc.update("""
            INSERT INTO payment_batch (id, source, channel, generated_on, content_hash, received_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """, b.id(), b.source(), b.channel().name(), b.generatedOn(), b.contentHash(),
            Timestamp.from(b.receivedAt()));
        return rows == 1;
    }

    @Override
    public Batch find(String id) {
        List<Batch> rows = jdbc.query("SELECT * FROM payment_batch WHERE id = ?", (rs, n) -> new Batch(
            rs.getString("id"),
            rs.getString("source"),
            BatchChannel.valueOf(rs.getString("channel")),
            rs.getObject("generated_on", LocalDate.class),
            rs.getString("content_hash"),
            rs.getTimestamp("received_at").toInstant(),
            rs.getInt("applied_lines"),
            rs.getInt("rejected_lines"),
            rs.getBigDecimal("applied_amount"),
            rs.getBigDecimal("rejected_amount")), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public void saveTotals(Batch b) {
        jdbc.update("""
            UPDATE payment_batch
            SET total_lines = ?, applied_lines = ?, rejected_lines = ?, applied_amount = ?, rejected_amount = ?
            WHERE id = ?
            """, b.totalLines(), b.appliedLines(), b.rejectedLines(), b.appliedAmount(), b.rejectedAmount(), b.id());
    }

    // --- PaymentLedger ---

    @Override
    public Set<String> findAppliedReferences(Collection<String> references) {
        if (references.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList(
            "SELECT reference FROM payment_line_result WHERE outcome = 'APLICADO' AND reference = ANY (?)",
            String.class, (Object) references.stream().distinct().toArray(String[]::new)));
    }

    /** Inserta en tandas de 1.000: con reWriteBatchedInserts el driver las junta en INSERT multi-fila (PR-02). */
    @Override
    public void record(String batchId, List<LineResult> results, String user, Instant processedAt) {
        jdbc.batchUpdate("""
            INSERT INTO payment_line_result (batch_id, line_number, reference, invoice_number, amount, paid_at,
                outcome, reason, detail, balance_before, balance_after, status_after, rounding_adjustment,
                processed_by, processed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, results, INSERT_BATCH_SIZE, (ps, r) -> {
                ps.setString(1, batchId);
                ps.setInt(2, r.lineNumber());
                ps.setString(3, r.reference());
                ps.setString(4, r.invoiceNumber());
                ps.setBigDecimal(5, r.amount());
                ps.setObject(6, r.paidAt());
                ps.setString(7, r.outcome().name());
                ps.setString(8, r.reason() == null ? null : r.reason().name());
                ps.setString(9, r.detail());
                ps.setBigDecimal(10, r.balanceBefore());
                ps.setBigDecimal(11, r.balanceAfter());
                ps.setString(12, r.statusAfter() == null ? null : r.statusAfter().name());
                ps.setBigDecimal(13, r.roundingAdjustment());
                ps.setString(14, user);
                ps.setTimestamp(15, Timestamp.from(processedAt));
            });
    }

    /**
     * Pagina por numero de linea con LIMIT/OFFSET: el resultado de un lote no cambia despues de
     * procesarlo, asi que no hay riesgo de saltar o repetir filas. La llave primaria
     * (batch_id, line_number) cubre el filtro por lote y el orden.
     */
    @Override
    public LinePage findLines(String batchId, LineOutcome outcome, RejectionReason reason, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE batch_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(batchId);
        if (outcome != null) {
            where.append(" AND outcome = ?");
            params.add(outcome.name());
        }
        if (reason != null) {
            where.append(" AND reason = ?");
            params.add(reason.name());
        }

        Long total = jdbc.queryForObject("SELECT count(*) FROM payment_line_result" + where, Long.class,
            params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((long) page * size);
        List<LineResult> items = jdbc.query("""
            SELECT line_number, reference, invoice_number, amount, paid_at, outcome, reason, detail,
                   balance_before, balance_after, status_after, rounding_adjustment
            FROM payment_line_result""" + where + " ORDER BY line_number LIMIT ? OFFSET ?",
            (rs, n) -> toLineResult(rs), pageParams.toArray());

        return new LinePage(items, page, size, total == null ? 0 : total);
    }

    /**
     * Con fetchSize, PostgreSQL entrega las filas en tandas de 1.000 en lugar de mandar el lote
     * entero de una vez. Solo funciona dentro de una transaccion (sin autocommit).
     */
    @Override
    public void forEachLine(String batchId, Consumer<LineResult> consumer) {
        jdbc.query(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                SELECT line_number, reference, invoice_number, amount, paid_at, outcome, reason, detail,
                       balance_before, balance_after, status_after, rounding_adjustment
                FROM payment_line_result
                WHERE batch_id = ?
                ORDER BY line_number
                """);
            ps.setFetchSize(INSERT_BATCH_SIZE);
            ps.setString(1, batchId);
            return ps;
        }, rs -> {
            consumer.accept(toLineResult(rs));
        });
    }

    private LineResult toLineResult(ResultSet rs) throws SQLException {
        String reason = rs.getString("reason");
        String statusAfter = rs.getString("status_after");
        return new LineResult(
            rs.getInt("line_number"),
            rs.getString("reference"),
            rs.getString("invoice_number"),
            rs.getBigDecimal("amount"),
            rs.getObject("paid_at", OffsetDateTime.class),
            LineOutcome.valueOf(rs.getString("outcome")),
            reason == null ? null : RejectionReason.valueOf(reason),
            rs.getString("detail"),
            rs.getBigDecimal("balance_before"),
            rs.getBigDecimal("balance_after"),
            statusAfter == null ? null : InvoiceStatus.valueOf(statusAfter),
            rs.getBigDecimal("rounding_adjustment"));
    }
}
