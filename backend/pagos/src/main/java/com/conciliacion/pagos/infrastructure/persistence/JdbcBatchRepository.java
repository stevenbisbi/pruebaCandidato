package com.conciliacion.pagos.infrastructure.persistence;

import com.conciliacion.pagos.application.port.out.BatchRepository;
import com.conciliacion.pagos.application.query.BatchHeader;
import com.conciliacion.pagos.application.query.BatchSummary;
import com.conciliacion.pagos.application.query.BatchTotals;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class JdbcBatchRepository implements BatchRepository {

    private static final String SELECT_BATCH = """
        SELECT id, source, channel, generated_on, content_hash, received_at,
               total_lines, applied_lines, rejected_lines, applied_amount, rejected_amount
        FROM payment_batch WHERE id = ?
        """;

    private final JdbcTemplate jdbc;

    public JdbcBatchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean register(BatchHeader h) {
        int inserted = jdbc.update("""
                INSERT INTO payment_batch (id, source, channel, generated_on, content_hash, received_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """,
            h.id(), h.source(), h.channel(), h.generatedOn(), h.contentHash(), Timestamp.from(h.receivedAt()));
        return inserted == 1;
    }

    @Override
    public Optional<BatchHeader> findHeader(String batchId) {
        return jdbc.query(SELECT_BATCH, (rs, n) -> header(rs), batchId).stream().findFirst();
    }

    @Override
    public void complete(String batchId, BatchTotals t) {
        jdbc.update("""
                UPDATE payment_batch
                SET total_lines = ?, applied_lines = ?, rejected_lines = ?, applied_amount = ?, rejected_amount = ?
                WHERE id = ?
                """,
            t.totalLines(), t.appliedLines(), t.rejectedLines(), t.appliedAmount(), t.rejectedAmount(), batchId);
    }

    @Override
    public Optional<BatchSummary> findSummary(String batchId) {
        return jdbc.query(SELECT_BATCH, (rs, n) -> {
            BatchTotals totals = new BatchTotals(rs.getInt("total_lines"), rs.getInt("applied_lines"),
                rs.getInt("rejected_lines"), rs.getBigDecimal("applied_amount"), rs.getBigDecimal("rejected_amount"));
            return new BatchSummary(header(rs), totals, rejectionsByReason(batchId));
        }, batchId).stream().findFirst();
    }

    private Map<String, Long> rejectionsByReason(String batchId) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT reason, count(*) AS total FROM payment_line_result
                WHERE batch_id = ? AND outcome = 'RECHAZADO'
                GROUP BY reason ORDER BY total DESC
                """,
            rs -> {
                result.put(rs.getString("reason"), rs.getLong("total"));
            }, batchId);
        return result;
    }

    private static BatchHeader header(ResultSet rs) throws SQLException {
        return new BatchHeader(rs.getString("id"), rs.getString("source"), rs.getString("channel"),
            rs.getObject("generated_on", LocalDate.class), rs.getString("content_hash"),
            rs.getTimestamp("received_at").toInstant());
    }
}
