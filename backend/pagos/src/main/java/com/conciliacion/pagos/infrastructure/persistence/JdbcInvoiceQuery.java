package com.conciliacion.pagos.infrastructure.persistence;

import com.conciliacion.pagos.application.port.out.InvoiceQueryPort;
import com.conciliacion.pagos.application.query.InvoiceFilter;
import com.conciliacion.pagos.application.query.InvoicePage;
import com.conciliacion.pagos.application.query.InvoiceView;
import com.conciliacion.pagos.domain.model.InvoiceStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Consulta paginada de facturas (RF-23) con paginacion por llave sobre (due_date, number).
 *
 * Se descarto LIMIT/OFFSET por PR-03: con OFFSET, si una factura sale del filtro (por ejemplo
 * filtrando por estado PENDIENTE mientras se aplica un lote) todas las siguientes se corren una
 * posicion y una se omite. Con cursor, la siguiente pagina empieza siempre despues de la ultima
 * llave vista, y la llave es inmutable.
 */
public class JdbcInvoiceQuery implements InvoiceQueryPort {

    private static final String SELECT = """
        SELECT i.number, i.supplier_nit, s.name AS supplier_name, i.issue_date, i.due_date,
               i.total_amount, i.balance, i.status
        FROM invoice i JOIN supplier s ON s.nit = i.supplier_nit
        """;

    private static final RowMapper<InvoiceView> MAPPER = (rs, n) -> new InvoiceView(
        rs.getString("number"), rs.getString("supplier_nit"), rs.getString("supplier_name"),
        rs.getObject("issue_date", LocalDate.class), rs.getObject("due_date", LocalDate.class),
        rs.getBigDecimal("total_amount"), rs.getBigDecimal("balance"), InvoiceStatus.valueOf(rs.getString("status")));

    private final JdbcTemplate jdbc;

    public JdbcInvoiceQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public InvoicePage search(InvoiceFilter f) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (f.supplierNit() != null) {
            sql.append(" AND i.supplier_nit = ?");
            args.add(f.supplierNit());
        }
        if (f.statuses() != null && !f.statuses().isEmpty()) {
            sql.append(" AND i.status IN (").append(String.join(",", f.statuses().stream().map(s -> "?").toList()))
                .append(")");
            f.statuses().forEach(s -> args.add(s.name()));
        }
        if (f.dueFrom() != null) {
            sql.append(" AND i.due_date >= ?");
            args.add(f.dueFrom());
        }
        if (f.dueTo() != null) {
            sql.append(" AND i.due_date <= ?");
            args.add(f.dueTo());
        }
        if (f.balanceMin() != null) {
            sql.append(" AND i.balance >= ?");
            args.add(f.balanceMin());
        }
        if (f.balanceMax() != null) {
            sql.append(" AND i.balance <= ?");
            args.add(f.balanceMax());
        }
        if (f.afterDueDate() != null && f.afterNumber() != null) {
            sql.append(" AND (i.due_date, i.number) > (?, ?)");
            args.add(f.afterDueDate());
            args.add(f.afterNumber());
        }
        // Se pide una fila de mas para saber si hay pagina siguiente sin hacer COUNT(*).
        sql.append(" ORDER BY i.due_date, i.number LIMIT ?");
        args.add(f.size() + 1);

        List<InvoiceView> rows = jdbc.query(sql.toString(), MAPPER, args.toArray());
        boolean hasNext = rows.size() > f.size();
        return new InvoicePage(hasNext ? rows.subList(0, f.size()) : rows, hasNext);
    }

    @Override
    public Optional<InvoiceView> findByNumber(String number) {
        return jdbc.query(SELECT + " WHERE i.number = ?", MAPPER, number).stream().findFirst();
    }
}
