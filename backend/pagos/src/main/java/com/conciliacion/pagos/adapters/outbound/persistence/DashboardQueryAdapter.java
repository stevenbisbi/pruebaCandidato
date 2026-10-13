package com.conciliacion.pagos.adapters.outbound.persistence;

import com.conciliacion.pagos.domain.model.ConciliationDashboard;
import com.conciliacion.pagos.domain.model.SupplierBalance;
import com.conciliacion.pagos.domain.repository.DashboardQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tablero de conciliacion (RF-24). Todas las cifras se filtran por la fecha de vencimiento de la
 * factura (A-08); los pagos se cruzan con su factura por el indice de V7.
 */
@Repository
public class DashboardQueryAdapter implements DashboardQueryPort {

    private static final int TOP_SUPPLIERS = 10;

    private final JdbcTemplate jdbc;

    public DashboardQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ConciliationDashboard summarize(LocalDate dueFrom, LocalDate dueTo) {
        List<Object> params = new ArrayList<>();
        String range = dueDateRange(dueFrom, dueTo, params);
        Object[] args = params.toArray();

        // 1. Facturas del rango: cuantas, cuanto se facturo y cuanto queda pendiente.
        Map<String, Object> invoices = jdbc.queryForMap("""
            SELECT count(*) AS invoice_count,
                   coalesce(sum(i.total_amount), 0) AS total_invoiced,
                   coalesce(sum(i.balance), 0) AS total_pending
            FROM invoice i
            WHERE TRUE""" + range, args);

        // 2. Pagos dirigidos a esas facturas, aplicados y rechazados. sum() ignora los montos nulos:
        //    una linea con valor ilegible cuenta como rechazo pero no suma (A-08).
        Map<String, Object> payments = jdbc.queryForMap("""
            SELECT count(*) FILTER (WHERE l.outcome = 'APLICADO') AS applied_count,
                   coalesce(sum(l.amount) FILTER (WHERE l.outcome = 'APLICADO'), 0) AS total_applied,
                   count(*) FILTER (WHERE l.outcome = 'RECHAZADO') AS rejected_count,
                   coalesce(sum(l.amount) FILTER (WHERE l.outcome = 'RECHAZADO'), 0) AS total_rejected
            FROM payment_line_result l
            JOIN invoice i ON i.number = l.invoice_number
            WHERE TRUE""" + range, args);

        // 3. Los diez proveedores con mayor saldo pendiente; el NIT desempata para que el orden sea estable.
        List<Object> topParams = new ArrayList<>(params);
        topParams.add(TOP_SUPPLIERS);
        List<SupplierBalance> top = jdbc.query("""
            SELECT s.nit, s.name, sum(i.balance) AS pending_balance, count(*) AS invoice_count
            FROM invoice i
            JOIN supplier s ON s.nit = i.supplier_nit
            WHERE TRUE""" + range + """

            GROUP BY s.nit, s.name
            HAVING sum(i.balance) > 0
            ORDER BY pending_balance DESC, s.nit
            LIMIT ?
            """, (rs, n) -> new SupplierBalance(
                rs.getString("nit"),
                rs.getString("name"),
                rs.getBigDecimal("pending_balance"),
                rs.getLong("invoice_count")), topParams.toArray());

        return new ConciliationDashboard(dueFrom, dueTo,
            ((Number) invoices.get("invoice_count")).longValue(),
            (BigDecimal) invoices.get("total_invoiced"),
            (BigDecimal) invoices.get("total_pending"),
            ((Number) payments.get("applied_count")).longValue(),
            (BigDecimal) payments.get("total_applied"),
            ((Number) payments.get("rejected_count")).longValue(),
            (BigDecimal) payments.get("total_rejected"),
            top);
    }

    private String dueDateRange(LocalDate dueFrom, LocalDate dueTo, List<Object> params) {
        StringBuilder range = new StringBuilder();
        if (dueFrom != null) {
            range.append(" AND i.due_date >= ?");
            params.add(dueFrom);
        }
        if (dueTo != null) {
            range.append(" AND i.due_date <= ?");
            params.add(dueTo);
        }
        return range.toString();
    }
}
