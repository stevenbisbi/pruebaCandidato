package com.conciliacion.pagos.domain.model;

import com.conciliacion.pagos.domain.service.DueDatePolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Factura con su saldo. Es mutable a proposito: dentro de un lote varios pagos a la misma
 * factura reducen el saldo de forma progresiva (RF-18), y el objeto vive solo mientras dura la
 * transaccion que tiene la fila bloqueada.
 */
public final class Invoice {

    public static final int MONEY_SCALE = 2;

    /** Un saldo de menos de un peso no se puede pagar en pesos enteros: la factura queda saldada (A-02). */
    private static final BigDecimal ONE_PESO = new BigDecimal("1.00");

    private final String number;
    private final BigDecimal totalAmount;
    private final LocalDate dueDate;
    private BigDecimal balance;
    private InvoiceStatus status;
    private boolean hadLatePayment;
    private boolean changed;

    /**
     * @param hadLatePayment si la factura ya recibio algun pago despues de su vencimiento (A-04)
     */
    public Invoice(String number, BigDecimal totalAmount, BigDecimal balance, LocalDate dueDate,
                   InvoiceStatus status, boolean hadLatePayment) {
        // 1. Primero se valida: si algo esta mal, el objeto no se crea.
        if (totalAmount.signum() <= 0) {
            throw new IllegalArgumentException("El total de la factura " + number + " debe ser positivo");
        }
        if (balance.signum() < 0 || balance.compareTo(totalAmount) > 0) {
            throw new IllegalArgumentException("El saldo de la factura " + number + " debe estar entre 0 y el total");
        }
        // 2. Despues se asignan los campos.
        this.number = number;
        this.totalAmount = totalAmount.setScale(MONEY_SCALE);
        this.balance = balance.setScale(MONEY_SCALE);
        this.dueDate = dueDate;
        this.status = status;
        this.hadLatePayment = hadLatePayment;
    }

    /**
     * Saldo que se le puede cobrar a Tesoreria: el saldo sin los centavos, porque Tesoreria paga en
     * pesos enteros (RF-10) y, cuando salda una factura, paga la parte entera (A-02). Ejemplo:
     * 37.500.483,40 se cobra como 37.500.483, y 30.202.062,72 como 30.202.062.
     */
    public BigDecimal payableBalance() {
        return balance.setScale(0, RoundingMode.FLOOR).setScale(MONEY_SCALE);
    }

    /** RF-09: el pago se acepta solo si cabe completo en el saldo a pagar. */
    public boolean accepts(BigDecimal amount) {
        return amount.compareTo(payableBalance()) <= 0;
    }

    /**
     * RF-07, RF-13, RF-14: descuenta el pago y recalcula el estado.
     *
     * @return ajuste por redondeo (A-02): los centavos condonados al saldar la factura (de 0,01 a
     *         0,99), o cero si no hubo ajuste. Nunca es negativo: el pago no puede superar el saldo.
     */
    public BigDecimal apply(BigDecimal amount, Instant paidAt) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("El valor del pago debe ser positivo");
        }
        if (!accepts(amount)) {
            throw new IllegalStateException("El pago excede el saldo de la factura " + number);
        }
        // A-04: basta un pago tardio para que la factura cierre como extemporanea.
        if (DueDatePolicy.isLate(dueDate, paidAt)) {
            hadLatePayment = true;
        }

        BigDecimal newBalance = balance.subtract(amount);
        BigDecimal adjustment = BigDecimal.ZERO.setScale(MONEY_SCALE);
        // Si queda menos de un peso, ya no hay nada que Tesoreria pueda pagar: la factura queda
        // saldada y los centavos se registran como ajuste. Como accepts() limita el pago a la parte
        // entera del saldo, el saldo nuevo nunca queda negativo.
        if (newBalance.compareTo(ONE_PESO) < 0) {
            adjustment = newBalance;
            newBalance = BigDecimal.ZERO.setScale(MONEY_SCALE);
        }
        balance = newBalance;
        status = InvoiceStatus.derive(totalAmount, balance, hadLatePayment);
        changed = true;
        return adjustment;
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

    public boolean hadLatePayment() {
        return hadLatePayment;
    }

    public boolean isChanged() {
        return changed;
    }
}
