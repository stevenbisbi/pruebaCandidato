package com.conciliacion.pagos.adapters.outbound.persistence;

import com.conciliacion.pagos.domain.model.InvoiceStatus;
import com.conciliacion.pagos.domain.model.LineOutcome;
import com.conciliacion.pagos.domain.model.LinePage;
import com.conciliacion.pagos.domain.model.LineResult;
import com.conciliacion.pagos.domain.model.RejectionReason;
import com.conciliacion.pagos.domain.repository.PaymentLedger;
import com.conciliacion.pagos.domain.service.DueDatePolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Implementacion con JDBC del puerto PaymentLedger (tabla payment_line_result). */
@Repository
public class JdbcPaymentLedger implements PaymentLedger {

    private static final String INSERT_SQL = """
        INSERT INTO payment_line_result (batch_id, line_number, reference, invoice_number, amount, paid_at,
            outcome, reason, detail, balance_before, balance_after, status_after, processed_by, processed_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    // Tipo SQL de cada "?" del INSERT, en el mismo orden. Hace falta porque varios valores pueden ser null.
    private static final int[] INSERT_TYPES = {
        Types.VARCHAR, Types.INTEGER, Types.VARCHAR, Types.VARCHAR, Types.NUMERIC, Types.TIMESTAMP,
        Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.NUMERIC, Types.NUMERIC, Types.VARCHAR,
        Types.VARCHAR, Types.TIMESTAMP
    };

    private final JdbcTemplate jdbc;

    public JdbcPaymentLedger(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<String> findAppliedReferences(List<String> references) {
        if (references.isEmpty()) {
            return new HashSet<>();
        }
        String sql = "SELECT reference FROM payment_line_result WHERE outcome = 'APLICADO' AND reference = ANY (?)";
        String[] referencesArray = references.toArray(new String[0]);
        List<String> found = jdbc.queryForList(sql, String.class, (Object) referencesArray);
        return new HashSet<>(found);
    }

    @Override
    public void record(String batchId, List<LineResult> results, String user, Instant processedAt) {
        List<Object[]> rows = new ArrayList<>();
        for (LineResult r : results) {
            rows.add(new Object[]{
                batchId,
                r.lineNumber(),
                cut(r.reference(), 80),
                cut(r.invoiceNumber(), 30),
                r.amount(),
                r.paidAt() == null ? null : Timestamp.from(r.paidAt().toInstant()),
                r.outcome().name(),
                r.reason() == null ? null : r.reason().name(),
                cut(r.detail(), 300),
                r.balanceBefore(),
                r.balanceAfter(),
                r.statusAfter() == null ? null : r.statusAfter().name(),
                user,
                Timestamp.from(processedAt)
            });
        }
        jdbc.batchUpdate(INSERT_SQL, rows, INSERT_TYPES);
    }

    /** El resultado de un lote no cambia nunca, asi que aqui LIMIT/OFFSET es estable. */
    @Override
    public LinePage findLines(String batchId, int page, int size) {
        Long total = jdbc.queryForObject(
            "SELECT count(*) FROM payment_line_result WHERE batch_id = ?", Long.class, batchId);
        List<LineResult> items = jdbc.query(
            "SELECT * FROM payment_line_result WHERE batch_id = ? ORDER BY line_number LIMIT ? OFFSET ?",
            (rs, rowNum) -> toLineResult(rs), batchId, size, page * size);
        return new LinePage(items, page, size, total);
    }

    private LineResult toLineResult(ResultSet rs) throws SQLException {
        // Las fechas se devuelven en hora de Colombia, no en la del servidor.
        Timestamp paidAtValue = rs.getTimestamp("paid_at");
        OffsetDateTime paidAt = null;
        if (paidAtValue != null) {
            paidAt = OffsetDateTime.ofInstant(paidAtValue.toInstant(), DueDatePolicy.OPERATION_ZONE);
        }
        String reason = rs.getString("reason");
        String status = rs.getString("status_after");
        return new LineResult(
            rs.getInt("line_number"),
            rs.getString("reference"),
            rs.getString("invoice_number"),
            rs.getBigDecimal("amount"),
            paidAt,
            LineOutcome.valueOf(rs.getString("outcome")),
            reason == null ? null : RejectionReason.valueOf(reason),
            rs.getString("detail"),
            rs.getBigDecimal("balance_before"),
            rs.getBigDecimal("balance_after"),
            status == null ? null : InvoiceStatus.valueOf(status));
    }

    /** Recorta un texto para que quepa en su columna (solo pasa con lineas invalidas). */
    private String cut(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
