package com.conciliacion.pagos.adapters.outbound.persistence;

import com.conciliacion.pagos.domain.model.InvoiceFilter;
import com.conciliacion.pagos.domain.model.InvoicePage;
import com.conciliacion.pagos.domain.model.InvoicePayment;
import com.conciliacion.pagos.domain.model.InvoiceStatus;
import com.conciliacion.pagos.domain.model.InvoiceView;
import com.conciliacion.pagos.domain.model.LineOutcome;
import com.conciliacion.pagos.domain.model.RejectionReason;
import com.conciliacion.pagos.domain.repository.InvoiceQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Consulta de facturas con filtros combinables (RF-23). Solo se agregan al WHERE los filtros que
 * vienen: asi PostgreSQL elige el indice que mejor sirve para esa combinacion.
 */
@Repository
public class InvoiceQueryAdapter implements InvoiceQueryPort {

    private final JdbcTemplate jdbc;

    public InvoiceQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public InvoicePage search(InvoiceFilter filter) {
        StringBuilder sql = new StringBuilder("""
            SELECT i.number, i.supplier_nit, s.name AS supplier_name, i.issue_date, i.due_date,
                   i.total_amount, i.balance, i.status
            FROM invoice i
            JOIN supplier s ON s.nit = i.supplier_nit
            WHERE TRUE""");
        List<Object> params = new ArrayList<>();

        if (filter.supplierNit() != null) {
            sql.append(" AND i.supplier_nit = ?");
            params.add(filter.supplierNit());
        }
        if (!filter.statuses().isEmpty()) {
            sql.append(" AND i.status = ANY (?)");
            params.add(filter.statuses().stream().map(InvoiceStatus::name).toArray(String[]::new));
        }
        if (filter.dueFrom() != null) {
            sql.append(" AND i.due_date >= ?");
            params.add(filter.dueFrom());
        }
        if (filter.dueTo() != null) {
            sql.append(" AND i.due_date <= ?");
            params.add(filter.dueTo());
        }
        if (filter.minBalance() != null) {
            sql.append(" AND i.balance >= ?");
            params.add(filter.minBalance());
        }
        if (filter.maxBalance() != null) {
            sql.append(" AND i.balance <= ?");
            params.add(filter.maxBalance());
        }
        // Paginacion por cursor (D-03): la pagina siguiente empieza despues de la ultima factura vista.
        if (filter.afterNumber() != null) {
            sql.append(" AND i.number > ?");
            params.add(filter.afterNumber());
        }
        // Se pide una fila de mas solo para saber si hay pagina siguiente.
        sql.append(" ORDER BY i.number LIMIT ?");
        params.add(filter.size() + 1);

        List<InvoiceView> rows = jdbc.query(sql.toString(), (rs, n) -> toView(rs), params.toArray());
        if (rows.size() <= filter.size()) {
            return new InvoicePage(rows, null);
        }
        List<InvoiceView> page = rows.subList(0, filter.size());
        return new InvoicePage(page, page.get(page.size() - 1).number());
    }

    @Override
    public Optional<InvoiceView> findByNumber(String number) {
        List<InvoiceView> rows = jdbc.query("""
            SELECT i.number, i.supplier_nit, s.name AS supplier_name, i.issue_date, i.due_date,
                   i.total_amount, i.balance, i.status
            FROM invoice i
            JOIN supplier s ON s.nit = i.supplier_nit
            WHERE i.number = ?
            """, (rs, n) -> toView(rs), number);
        return rows.stream().findFirst();
    }

    /** Usa el indice por numero de factura de V7. */
    @Override
    public List<InvoicePayment> findPayments(String number) {
        return jdbc.query("""
            SELECT batch_id, line_number, reference, amount, paid_at, outcome, reason, balance_before,
                   balance_after, status_after, rounding_adjustment, processed_by, processed_at
            FROM payment_line_result
            WHERE invoice_number = ?
            ORDER BY processed_at, batch_id, line_number
            """, (rs, n) -> toPayment(rs), number);
    }

    private InvoicePayment toPayment(ResultSet rs) throws SQLException {
        String reason = rs.getString("reason");
        String statusAfter = rs.getString("status_after");
        return new InvoicePayment(
            rs.getString("batch_id"),
            rs.getInt("line_number"),
            rs.getString("reference"),
            rs.getBigDecimal("amount"),
            rs.getObject("paid_at", OffsetDateTime.class),
            LineOutcome.valueOf(rs.getString("outcome")),
            reason == null ? null : RejectionReason.valueOf(reason),
            rs.getBigDecimal("balance_before"),
            rs.getBigDecimal("balance_after"),
            statusAfter == null ? null : InvoiceStatus.valueOf(statusAfter),
            rs.getBigDecimal("rounding_adjustment"),
            rs.getString("processed_by"),
            rs.getTimestamp("processed_at").toInstant());
    }

    private InvoiceView toView(ResultSet rs) throws SQLException {
        return new InvoiceView(
            rs.getString("number"),
            rs.getString("supplier_nit"),
            rs.getString("supplier_name"),
            rs.getObject("issue_date", LocalDate.class),
            rs.getObject("due_date", LocalDate.class),
            rs.getBigDecimal("total_amount"),
            rs.getBigDecimal("balance"),
            InvoiceStatus.valueOf(rs.getString("status")));
    }
}
