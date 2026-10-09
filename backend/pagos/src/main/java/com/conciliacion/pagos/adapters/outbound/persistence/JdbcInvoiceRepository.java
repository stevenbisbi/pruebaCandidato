package com.conciliacion.pagos.adapters.outbound.persistence;

import com.conciliacion.pagos.domain.model.Invoice;
import com.conciliacion.pagos.domain.model.InvoiceStatus;
import com.conciliacion.pagos.domain.repository.InvoiceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Implementacion con JDBC del puerto InvoiceRepository. */
@Repository
public class JdbcInvoiceRepository implements InvoiceRepository {

    private final JdbcTemplate jdbc;

    public JdbcInvoiceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * FOR UPDATE bloquea las filas hasta el fin de la transaccion: si otro lote quiere la misma
     * factura, espera y luego ve el saldo ya actualizado (CF-04).
     * ORDER BY number: todos los lotes bloquean en el mismo orden, asi nunca se esperan en circulo.
     */
    @Override
    public Map<String, Invoice> lockByNumbers(List<String> numbers) {
        Map<String, Invoice> result = new HashMap<>();
        if (numbers.isEmpty()) {
            return result;
        }
        String sql = """
            SELECT number, total_amount, balance, due_date, status
            FROM invoice
            WHERE number = ANY (?)
            ORDER BY number
            FOR UPDATE
            """;
        String[] numbersArray = numbers.toArray(new String[0]);
        List<Invoice> rows = jdbc.query(sql, (rs, rowNum) -> toInvoice(rs), (Object) numbersArray);
        for (Invoice invoice : rows) {
            result.put(invoice.number(), invoice);
        }
        return result;
    }

    @Override
    public void saveBalances(List<Invoice> invoices) {
        List<Object[]> rows = new ArrayList<>();
        for (Invoice invoice : invoices) {
            rows.add(new Object[]{invoice.balance(), invoice.status().name(), invoice.number()});
        }
        jdbc.batchUpdate("UPDATE invoice SET balance = ?, status = ? WHERE number = ?", rows);
    }

    private Invoice toInvoice(ResultSet rs) throws SQLException {
        return new Invoice(
            rs.getString("number"),
            rs.getBigDecimal("total_amount"),
            rs.getBigDecimal("balance"),
            rs.getObject("due_date", LocalDate.class),
            InvoiceStatus.valueOf(rs.getString("status")));
    }
}
