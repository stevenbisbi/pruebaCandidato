package com.conciliacion.pagos.domain.model;

import java.math.BigDecimal;

/**
 * Estados de una factura (RF-03, RF-13). Los nombres son vocabulario de negocio de Cartera,
 * por eso se conservan en espanol y coinciden con lo que se persiste y se expone por la API.
 */
public enum InvoiceStatus {
    PENDIENTE,
    PARCIAL,
    PAGADA,
    PAGADA_EXTEMPORANEA;

    /**
     * Deriva el estado a partir del saldo (RF-14). El estado nunca se asigna a mano:
     * siempre se recalcula despues de aplicar un pago.
     *
     * @param closedLate si el pago que llevo el saldo a cero ocurrio despues del vencimiento
     */
    public static InvoiceStatus derive(BigDecimal totalAmount, BigDecimal balance, boolean closedLate) {
        if (balance.signum() == 0) {
            return closedLate ? PAGADA_EXTEMPORANEA : PAGADA;
        }
        if (balance.compareTo(totalAmount) == 0) {
            return PENDIENTE;
        }
        return PARCIAL;
    }
}
