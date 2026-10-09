package com.conciliacion.pagos.infrastructure.persistence;

import com.conciliacion.pagos.application.port.out.DashboardQueryPort;
import com.conciliacion.pagos.application.query.Dashboard;
import com.conciliacion.pagos.domain.service.DueDatePolicy;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Tablero (RF-24). El rango filtra facturas por fecha de vencimiento y pagos por fecha de pago
 * en hora de Colombia (ver DECISIONS.md, ambiguedad del tablero).
 */
public class JdbcDashboardQuery implements DashboardQueryPort {

    private final JdbcTemplate jdbc;

    public JdbcDashboardQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Dashboard load(LocalDate from, LocalDate to) {
        Range dueRange = dueRange(from, to);
        Range paidRange = paidRange(from, to);

        Object[] invoiceTotals = jdbc.queryForObject(
            "SELECT coalesce(sum(total_amount), 0), count(*), coalesce(sum(balance), 0) FROM invoice i"
                + dueRange.where, (rs, n) -> new Object[]{rs.getBigDecimal(1), rs.getLong(2), rs.getBigDecimal(3)},
            dueRange.args.toArray());

        BigDecimal applied = BigDecimal.ZERO;
        BigDecimal rejected = BigDecimal.ZERO;
        long appliedCount = 0;
        long rejectedCount = 0;
        List<Object[]> paymentTotals = jdbc.query(
            "SELECT outcome, coalesce(sum(amount), 0), count(*) FROM payment_line_result r"
                + paidRange.where + " GROUP BY outcome",
            (rs, n) -> new Object[]{rs.getString(1), rs.getBigDecimal(2), rs.getLong(3)}, paidRange.args.toArray());
        for (Object[] row : paymentTotals) {
            if ("APLICADO".equals(row[0])) {
                applied = (BigDecimal) row[1];
                appliedCount = (Long) row[2];
            } else {
                rejected = (BigDecimal) row[1];
                rejectedCount = (Long) row[2];
            }
        }

        List<Dashboard.SupplierBalance> top = jdbc.query("""
                SELECT s.nit, s.name, t.pending FROM (
                    SELECT supplier_nit, sum(balance) AS pending FROM invoice i
                """ + dueRange.where + """
                    GROUP BY supplier_nit ORDER BY pending DESC, supplier_nit LIMIT 10
                ) t JOIN supplier s ON s.nit = t.supplier_nit
                ORDER BY t.pending DESC, s.nit
                """,
            (rs, n) -> new Dashboard.SupplierBalance(rs.getString(1), rs.getString(2), rs.getBigDecimal(3)),
            dueRange.args.toArray());

        return new Dashboard((BigDecimal) invoiceTotals[0], (Long) invoiceTotals[1], (BigDecimal) invoiceTotals[2],
            applied, appliedCount, rejected, rejectedCount, top);
    }

    private static Range dueRange(LocalDate from, LocalDate to) {
        Range range = new Range();
        if (from != null) {
            range.add("i.due_date >= ?", from);
        }
        if (to != null) {
            range.add("i.due_date <= ?", to);
        }
        return range;
    }

    /** Los limites del dia se calculan en hora de Colombia, no en la del servidor. */
    private static Range paidRange(LocalDate from, LocalDate to) {
        Range range = new Range();
        if (from != null) {
            range.add("r.paid_at >= ?", Timestamp.from(from.atStartOfDay(DueDatePolicy.OPERATION_ZONE).toInstant()));
        }
        if (to != null) {
            range.add("r.paid_at < ?",
                Timestamp.from(to.plusDays(1).atStartOfDay(DueDatePolicy.OPERATION_ZONE).toInstant()));
        }
        return range;
    }

    private static final class Range {
        private String where = " ";
        private final List<Object> args = new ArrayList<>();

        void add(String condition, Object value) {
            where = (args.isEmpty() ? " WHERE " : where + " AND ") + condition + " ";
            args.add(value);
        }
    }
}
