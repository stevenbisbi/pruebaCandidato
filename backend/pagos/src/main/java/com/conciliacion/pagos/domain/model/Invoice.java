package com.conciliacion.pagos.domain.model;

import com.conciliacion.pagos.domain.service.DueDatePolicy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Factura con su saldo. Es mutable a proposito: dentro de un lote varios pagos a la misma
 * factura reducen el saldo de forma progresiva (RF-18), y el objeto vive solo mientras dura la
 * transaccion que tiene la fila bloqueada.
 */
public final class Invoice {

    public static final int MONEY_SCALE = 2;

    private final String number;
    private final BigDecimal totalAmount;
    private final LocalDate dueDate;
    private BigDecimal balance;
    private InvoiceStatus status;
    private boolean changed;

    public Invoice(String number, BigDecimal totalAmount, BigDecimal balance, LocalDate dueDate,
                   InvoiceStatus status) {
        this.number = number;
        this.totalAmount = totalAmount.setScale(MONEY_SCALE);
        this.balance = balance.setScale(MONEY_SCALE);
        this.dueDate = dueDate;
        this.status = status;
    }

    /** RF-09: el pago se acepta solo si cabe completo en el saldo. */
    public boolean accepts(BigDecimal amount) {
        return amount.compareTo(balance) <= 0;
    }

    /** RF-07, RF-13, RF-14: descuenta el pago y recalcula el estado. */
    public void apply(BigDecimal amount, Instant paidAt) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("El valor del pago debe ser positivo");
        }
        if (!accepts(amount)) {
            throw new IllegalStateException("El pago excede el saldo de la factura " + number);
        }
        balance = balance.subtract(amount);
        status = InvoiceStatus.derive(totalAmount, balance, DueDatePolicy.isLate(dueDate, paidAt));
        changed = true;
    }

    public String number() {
        return number;
    }

    public BigDecimal totalAmount() {
        return totalAmount;
    }

    public BigDecimal balance() {
        return balance;
    }

    public LocalDate dueDate() {
        return dueDate;
    }

    public InvoiceStatus status() {
        return status;
    }

    public boolean isChanged() {
        return changed;
    }
}
