package com.conciliacion.pagos.adapters.inbound.rest.dto;

import com.conciliacion.pagos.domain.model.InvoiceDetail;
import com.conciliacion.pagos.domain.model.InvoicePayment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/** Una factura y los pagos que recibio, aplicados y rechazados, con su auditoria (RF-15). */
public record InvoiceDetailResponseDto(InvoiceResponseDto invoice, List<PaymentDto> payments) {

    /** Un pago dirigido a la factura. Los montos van como texto (RF-05). */
    public record PaymentDto(String batchId, int lineNumber, String reference, String amount, OffsetDateTime paidAt,
                             String outcome, String reason, String balanceBefore, String balanceAfter,
                             String statusAfter, String roundingAdjustment, String processedBy, Instant processedAt) {

        static PaymentDto from(InvoicePayment p) {
            return new PaymentDto(p.batchId(), p.lineNumber(), p.reference(), text(p.amount()), p.paidAt(),
                p.outcome().name(), p.reason() == null ? null : p.reason().name(),
                text(p.balanceBefore()), text(p.balanceAfter()),
                p.statusAfter() == null ? null : p.statusAfter().name(),
                text(p.roundingAdjustment()), p.processedBy(), p.processedAt());
        }
    }

    public static InvoiceDetailResponseDto from(InvoiceDetail detail) {
        return new InvoiceDetailResponseDto(
            InvoiceResponseDto.from(detail.invoice()),
            detail.payments().stream().map(PaymentDto::from).toList());
    }

    private static String text(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
