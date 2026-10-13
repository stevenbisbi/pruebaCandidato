package com.conciliacion.pagos.adapters.inbound.rest.dto;

import com.conciliacion.pagos.domain.model.LineResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Una linea del resultado de un lote (RF-21). Los montos van como texto para no perder precision
 * (RF-05); los que no aplican (por ejemplo el saldo de una factura inexistente) van en null.
 *
 * @param reasonDescription el motivo de rechazo en palabras, para mostrarlo sin traducir el codigo
 */
public record LineResultResponseDto(int lineNumber, String reference, String invoiceNumber, String amount,
                                    OffsetDateTime paidAt, String outcome, String reason,
                                    String reasonDescription, String detail, String balanceBefore,
                                    String balanceAfter, String statusAfter, String roundingAdjustment) {

    public static LineResultResponseDto from(LineResult line) {
        return new LineResultResponseDto(
            line.lineNumber(),
            line.reference(),
            line.invoiceNumber(),
            text(line.amount()),
            line.paidAt(),
            line.outcome().name(),
            line.reason() == null ? null : line.reason().name(),
            line.reason() == null ? null : line.reason().description(),
            line.detail(),
            text(line.balanceBefore()),
            text(line.balanceAfter()),
            line.statusAfter() == null ? null : line.statusAfter().name(),
            text(line.roundingAdjustment()));
    }

    private static String text(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
