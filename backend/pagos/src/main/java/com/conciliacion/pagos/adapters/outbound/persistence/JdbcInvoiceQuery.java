package com.conciliacion.pagos.adapters.outbound.persistence;

import com.conciliacion.pagos.domain.model.InvoiceFilter;
import com.conciliacion.pagos.domain.model.InvoicePage;
import com.conciliacion.pagos.domain.model.InvoiceStatus;
import com.conciliacion.pagos.domain.model.InvoiceView;
import com.conciliacion.pagos.domain.repository.InvoiceQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Consulta de facturas con filtros (RF-23).
 *
 * Paginacion (PR-03): en vez de "salta N filas" (OFFSET), cada pagina pide las facturas cuyo
 * numero es mayor que el ultimo de la pagina anterior. El numero nunca cambia, asi que aunque un
 * lote cambie estados mientras se navega, no se repiten ni se saltan facturas.
 */
@Repository
public class JdbcInvoiceQuery implements InvoiceQueryPort {

    private final JdbcTemplate jdbc;

    public JdbcInvoiceQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public InvoicePage search(InvoiceFilter filter) {
        String sql = """
            SELECT i.number, i.supplier_nit, s.name AS supplier_name, i.issue_date, i.due_date,
                   i.total_amount, i.balance, i.status
            FROM invoice i JOIN supplier s ON s.nit = i.supplier_nit
            WHERE 1 = 1
            """;
        List<Object> params = new ArrayList<>();

        if (filter.supplierNit() != null) {
            sql += " AND i.supplier_nit = ?";
            params.add(filter.supplierNit());
        }
        if (filter.statuses() != null && !filter.statuses().isEmpty()) {
            List<String> statusNames = new ArrayList<>();
            for (InvoiceStatus status : filter.statuses()) {
                statusNames.add(status.name());
            }
            sql += " AND i.status = ANY (?)";
            params.add(statusNames.toArray(new String[0]));
        }
        if (filter.afterNumber() != null) {
            sql += " AND i.number > ?";
            params.add(filter.afterNumber());
        }

        // Se pide una fila de mas solo para saber si hay pagina siguiente.
        sql += " ORDER BY i.number LIMIT ?";
        params.add(filter.size() + 1);

        List<InvoiceView> rows = jdbc.query(sql, (rs, rowNum) -> toInvoiceView(rs), params.toArray());
        boolean hasNext = rows.size() > filter.size();
        if (hasNext) {
            rows = rows.subList(0, filter.size());
        }
        return new InvoicePage(rows, hasNext);
    }

    private InvoiceView toInvoiceView(ResultSet rs) throws SQLException {
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
