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
import java.util.Collections;
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

    private static final String SELECT_VIEW = """
        SELECT i.number, i.supplier_nit, s.name AS supplier_name, i.issue_date, i.due_date,
               i.total_amount, i.balance, i.status
        FROM invoice i
        JOIN supplier s ON s.nit = i.supplier_nit
        WHERE TRUE""";

    @Override
    public InvoicePage search(InvoiceFilter filter) {
        if (filter.afterNumber() == null && filter.page() > 1) {
            return jumpToPage(filter);
        }
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_VIEW).append(where(filter, params));
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

    /** Sin join con proveedores: el filtro no lo necesita y el conteo es mas barato (D-03). */
    @Override
    public long count(InvoiceFilter filter) {
        List<Object> params = new ArrayList<>();
        Long total = jdbc.queryForObject("SELECT count(*) FROM invoice i WHERE TRUE" + where(filter, params),
            Long.class, params.toArray());
        return total == null ? 0 : total;
    }

    /**
     * Salto directo a la pagina N, sin cursor (D-03). OFFSET obliga a recorrer todas las filas
     * anteriores, asi que se posiciona desde el extremo mas cercano: una pagina de la segunda mitad
     * (la ultima incluida) se lee al reves desde el final y se le da vuelta. Nunca se recorre mas de
     * la mitad del resultado. Una pagina mas alla del final devuelve la ultima.
     */
    private InvoicePage jumpToPage(InvoiceFilter filter) {
        long total = count(filter);
        int size = filter.size();
        long lastPage = Math.max(1, (total + size - 1) / size);
        long offset = (Math.min(filter.page(), lastPage) - 1) * size;
        int rowsInPage = (int) Math.min(size, total - offset);
        if (rowsInPage <= 0) {
            return new InvoicePage(List.of(), null);
        }

        List<Object> params = new ArrayList<>();
        String where = where(filter, params);
        List<InvoiceView> rows;
        if (offset <= total / 2) {
            params.add(rowsInPage);
            params.add(offset);
            rows = jdbc.query(SELECT_VIEW + where + " ORDER BY i.number LIMIT ? OFFSET ?",
                (rs, n) -> toView(rs), params.toArray());
        } else {
            params.add(rowsInPage);
            params.add(total - offset - rowsInPage);
            rows = new ArrayList<>(jdbc.query(SELECT_VIEW + where + " ORDER BY i.number DESC LIMIT ? OFFSET ?",
                (rs, n) -> toView(rs), params.toArray()));
            Collections.reverse(rows);
        }
        // Desde aqui, "Siguiente" vuelve a ser por cursor: el recorrido pagina a pagina es estable (PR-03).
        boolean hasNext = !rows.isEmpty() && offset + rows.size() < total;
        return new InvoicePage(rows, hasNext ? rows.get(rows.size() - 1).number() : null);
    }

    /** Las condiciones de los filtros que vienen; los que no vienen no se agregan (RF-23). */
    private String where(InvoiceFilter filter, List<Object> params) {
        StringBuilder where = new StringBuilder();
        if (filter.supplierNit() != null) {
            where.append(" AND i.supplier_nit = ?");
            params.add(filter.supplierNit());
        }
        if (!filter.statuses().isEmpty()) {
            where.append(" AND i.status = ANY (?)");
            params.add(filter.statuses().stream().map(InvoiceStatus::name).toArray(String[]::new));
        }
        if (filter.dueFrom() != null) {
            where.append(" AND i.due_date >= ?");
            params.add(filter.dueFrom());
        }
        if (filter.dueTo() != null) {
            where.append(" AND i.due_date <= ?");
            params.add(filter.dueTo());
        }
        if (filter.minBalance() != null) {
            where.append(" AND i.balance >= ?");
            params.add(filter.minBalance());
        }
        if (filter.maxBalance() != null) {
            where.append(" AND i.balance <= ?");
            params.add(filter.maxBalance());
        }
        return where.toString();
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
