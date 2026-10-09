package com.conciliacion.pagos.infrastructure.persistence;

import com.conciliacion.pagos.application.port.out.InvoiceRepository;
import com.conciliacion.pagos.domain.model.Invoice;
import com.conciliacion.pagos.domain.model.InvoiceStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class JdbcInvoiceRepository implements InvoiceRepository {

    // ORDER BY antes de FOR UPDATE: PostgreSQL bloquea en el orden en que entrega las filas, asi
    // que dos lotes concurrentes toman los bloqueos en el mismo orden y no hay interbloqueo.
    private static final String LOCK_SQL = """
        SELECT number, total_amount, balance, due_date, status
        FROM invoice
        WHERE number = ANY (?)
        ORDER BY number
        FOR UPDATE
        """;

    // Una sola sentencia para todo el lote en lugar de N updates: es lo que permite PR-02.
    private static final String UPDATE_SQL = """
        UPDATE invoice i
        SET balance = u.balance, status = u.status
        FROM unnest(?::varchar[], ?::numeric[], ?::varchar[]) AS u(number, balance, status)
        WHERE i.number = u.number
        """;

    private final JdbcTemplate jdbc;

    public JdbcInvoiceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Invoice> lockByNumbers(Collection<String> numbers) {
        Map<String, Invoice> result = new HashMap<>(numbers.size() * 2);
        if (numbers.isEmpty()) {
            return result;
        }
        String[] keys = numbers.toArray(String[]::new);
        jdbc.query(con -> {
            var ps = con.prepareStatement(LOCK_SQL);
            ps.setArray(1, con.createArrayOf("varchar", keys));
            return ps;
        }, rs -> {
            Invoice invoice = new Invoice(
                rs.getString("number"),
                rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("balance"),
                rs.getObject("due_date", java.time.LocalDate.class),
                InvoiceStatus.valueOf(rs.getString("status")));
            result.put(invoice.number(), invoice);
        });
        return result;
    }

    @Override
    public void saveBalances(Collection<Invoice> invoices) {
        if (invoices.isEmpty()) {
            return;
        }
        String[] numbers = new String[invoices.size()];
        BigDecimal[] balances = new BigDecimal[invoices.size()];
        String[] statuses = new String[invoices.size()];
        int i = 0;
        for (Invoice invoice : invoices) {
            numbers[i] = invoice.number();
            balances[i] = invoice.balance();
            statuses[i] = invoice.status().name();
            i++;
        }
        jdbc.update(con -> {
            var ps = con.prepareStatement(UPDATE_SQL);
            ps.setArray(1, con.createArrayOf("varchar", numbers));
            ps.setArray(2, con.createArrayOf("numeric", balances));
            ps.setArray(3, con.createArrayOf("varchar", statuses));
            return ps;
        });
    }
}
