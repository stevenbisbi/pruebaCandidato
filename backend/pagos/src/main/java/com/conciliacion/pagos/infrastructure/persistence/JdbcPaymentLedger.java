package com.conciliacion.pagos.infrastructure.persistence;

import com.conciliacion.pagos.application.port.out.PaymentLedger;
import com.conciliacion.pagos.application.query.LinePage;
import com.conciliacion.pagos.domain.model.InvoiceStatus;
import com.conciliacion.pagos.domain.model.LineOutcome;
import com.conciliacion.pagos.domain.model.LineResult;
import com.conciliacion.pagos.domain.model.RejectionReason;
import com.conciliacion.pagos.domain.service.DueDatePolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class JdbcPaymentLedger implements PaymentLedger {

    private static final int INSERT_CHUNK = 2_000;
    private static final String COLUMNS = """
        line_number, reference, invoice_number, amount, paid_at, outcome, reason, detail,
        balance_before, balance_after, status_after
        """;

    /** Las fechas se devuelven en hora de Colombia, no en la zona del contenedor. */
    private static final RowMapper<LineResult> MAPPER = (rs, n) -> {
        Timestamp paidAt = rs.getTimestamp("paid_at");
        String reason = rs.getString("reason");
        String status = rs.getString("status_after");
        return new LineResult(rs.getInt("line_number"), rs.getString("reference"), rs.getString("invoice_number"),
            rs.getBigDecimal("amount"),
            paidAt == null ? null : OffsetDateTime.ofInstant(paidAt.toInstant(), DueDatePolicy.OPERATION_ZONE),
            LineOutcome.valueOf(rs.getString("outcome")),
            reason == null ? null : RejectionReason.valueOf(reason), rs.getString("detail"),
            rs.getBigDecimal("balance_before"), rs.getBigDecimal("balance_after"),
            status == null ? null : InvoiceStatus.valueOf(status));
    };

    private final JdbcTemplate jdbc;
    private final JdbcTemplate streamingJdbc;
    private final TransactionTemplate readOnlyTx;

    public JdbcPaymentLedger(JdbcTemplate jdbc, TransactionTemplate readOnlyTx) {
        this.jdbc = jdbc;
        this.streamingJdbc = new JdbcTemplate(jdbc.getDataSource());
        this.streamingJdbc.setFetchSize(1_000);
        this.readOnlyTx = readOnlyTx;
    }

    @Override
    public Set<String> findAppliedReferences(Collection<String> references) {
        Set<String> result = new HashSet<>();
        if (references.isEmpty()) {
            return result;
        }
        String[] keys = references.toArray(String[]::new);
        jdbc.query(con -> {
            var ps = con.prepareStatement(
                "SELECT reference FROM payment_line_result WHERE outcome = 'APLICADO' AND reference = ANY (?)");
            ps.setArray(1, con.createArrayOf("varchar", keys));
            return ps;
        }, rs -> {
            result.add(rs.getString(1));
        });
        return result;
    }

    @Override
    public void record(String batchId, List<LineResult> results, String user, Instant processedAt) {
        String sql = "INSERT INTO payment_line_result (batch_id, " + COLUMNS
            + ", processed_by, processed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Timestamp at = Timestamp.from(processedAt);
        jdbc.batchUpdate(sql, results, INSERT_CHUNK, (ps, r) -> {
            ps.setString(1, batchId);
            ps.setInt(2, r.lineNumber());
            ps.setString(3, truncate(r.reference(), 80));
            ps.setString(4, truncate(r.invoiceNumber(), 30));
            ps.setBigDecimal(5, r.amount());
            if (r.paidAt() == null) {
                ps.setNull(6, Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                ps.setTimestamp(6, Timestamp.from(r.paidAt().toInstant()));
            }
            ps.setString(7, r.outcome().name());
            ps.setString(8, r.reason() == null ? null : r.reason().name());
            ps.setString(9, truncate(r.detail(), 300));
            ps.setBigDecimal(10, r.balanceBefore());
            ps.setBigDecimal(11, r.balanceAfter());
            ps.setString(12, r.statusAfter() == null ? null : r.statusAfter().name());
            ps.setString(13, user);
            ps.setTimestamp(14, at);
        });
    }

    @Override
    public LinePage findLines(String batchId, LineOutcome outcome, RejectionReason reason, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE batch_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(batchId);
        if (outcome != null) {
            where.append(" AND outcome = ?");
            args.add(outcome.name());
        }
        if (reason != null) {
            where.append(" AND reason = ?");
            args.add(reason.name());
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM payment_line_result" + where, Long.class,
            args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<LineResult> items = jdbc.query("SELECT " + COLUMNS + " FROM payment_line_result" + where
            + " ORDER BY line_number LIMIT ? OFFSET ?", MAPPER, pageArgs.toArray());
        return new LinePage(items, page, size, total == null ? 0 : total);
    }

    @Override
    public void streamLines(String batchId, Consumer<LineResult> consumer) {
        // El fetch size solo se respeta con autocommit desactivado, de ahi la transaccion de lectura.
        readOnlyTx.executeWithoutResult(status -> streamingJdbc.query(
            "SELECT " + COLUMNS + " FROM payment_line_result WHERE batch_id = ? ORDER BY line_number",
            rs -> {
                consumer.accept(MAPPER.mapRow(rs, 0));
            }, batchId));
    }

    @Override
    public List<LineResult> findByInvoice(String invoiceNumber) {
        return jdbc.query("SELECT " + COLUMNS + " FROM payment_line_result WHERE invoice_number = ?"
            + " ORDER BY processed_at, batch_id, line_number LIMIT 500", MAPPER, invoiceNumber);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
