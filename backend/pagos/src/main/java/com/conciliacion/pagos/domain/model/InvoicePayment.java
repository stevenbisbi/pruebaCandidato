package com.conciliacion.pagos.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * Un pago dirigido a una factura, aplicado o rechazado, con su auditoria (RF-15): quien lo
 * proceso, cuando, y el saldo antes y despues.
 */
public record InvoicePayment(String batchId, int lineNumber, String reference, BigDecimal amount,
                             OffsetDateTime paidAt, LineOutcome outcome, RejectionReason reason,
                             BigDecimal balanceBefore, BigDecimal balanceAfter, InvoiceStatus statusAfter,
                             BigDecimal roundingAdjustment, String processedBy, Instant processedAt) {
}
