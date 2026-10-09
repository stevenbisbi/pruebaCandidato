package com.conciliacion.pagos.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Resultado de una linea. Es a la vez el registro de auditoria (RF-15): guarda saldo anterior y
 * posterior incluso en los rechazos, donde ambos coinciden (RF-11).
 *
 * @param amount valor interpretado, o null si no se pudo interpretar
 * @param paidAt fecha de pago interpretada, o null si no se pudo interpretar
 * @param detail texto libre con el dato que causo el rechazo, para el operador
 * @param balanceBefore null cuando la factura no existe
 */
public record LineResult(int lineNumber, String reference, String invoiceNumber, BigDecimal amount,
                         OffsetDateTime paidAt, LineOutcome outcome, RejectionReason reason, String detail,
                         BigDecimal balanceBefore, BigDecimal balanceAfter, InvoiceStatus statusAfter) {

    public boolean isApplied() {
        return outcome == LineOutcome.APLICADO;
    }
}
