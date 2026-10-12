package com.conciliacion.pagos.adapters.outbound.persistence;

import com.conciliacion.pagos.domain.repository.InvoiceRepository;

import com.conciliacion.pagos.domain.model.InvoiceStatus;
import com.conciliacion.pagos.domain.model.Invoice;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/** Facturas en PostgreSQL: bloqueo para aplicar un lote y guardado de saldos. */
@Repository
public class InvoicePersistenceAdapter implements InvoiceRepository {

    private final JdbcTemplate jdbc;

    public InvoicePersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * FOR UPDATE bloquea las filas hasta el fin de la transaccion: si otro lote quiere la misma
     * factura, espera y despues ve el saldo ya reducido (CF-04, D-02). ORDER BY number: todos los
     * lotes bloquean en el mismo orden, asi dos lotes nunca se esperan mutuamente.
     */
    @Override
    public Map<String, Invoice> lockByNumbers(List<String> numbers) {
        Map<String, Invoice> result = new HashMap<>();
        if (numbers.isEmpty()) {
            return result;
        }
        String sql = """
            SELECT number, total_amount, balance, due_date, status, had_late_payment
            FROM invoice
            WHERE number = ANY (?)
            ORDER BY number
            FOR UPDATE
            """;
        String[] distinct = numbers.stream().distinct().toArray(String[]::new);
        jdbc.query(sql, rs -> {
            Invoice invoice = new Invoice(
                rs.getString("number"),
                rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("balance"),
                rs.getObject("due_date", LocalDate.class),
                InvoiceStatus.valueOf(rs.getString("status")),
                rs.getBoolean("had_late_payment"));
            result.put(invoice.number(), invoice);
        }, (Object) distinct);
        return result;
    }

    @Override
    public void saveBalances(List<Invoice> invoices) {
        jdbc.batchUpdate("UPDATE invoice SET balance = ?, status = ?, had_late_payment = ? WHERE number = ?",
            invoices.stream()
                .map(i -> new Object[]{i.balance(), i.status().name(), i.hadLatePayment(), i.number()})
                .toList());
    }
}
