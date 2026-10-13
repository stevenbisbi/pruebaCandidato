package com.conciliacion.pagos.adapters.inbound.rest.dto;

import com.conciliacion.pagos.domain.model.InvoiceView;

import java.time.LocalDate;

/** Una factura en la consulta (RF-01, RF-23). Los montos van como texto para no perder precision (RF-05). */
public record InvoiceResponseDto(String number, String supplierNit, String supplierName, LocalDate issueDate,
                                 LocalDate dueDate, String totalAmount, String balance, String status) {

    public static InvoiceResponseDto from(InvoiceView invoice) {
        return new InvoiceResponseDto(
            invoice.number(),
            invoice.supplierNit(),
            invoice.supplierName(),
            invoice.issueDate(),
            invoice.dueDate(),
            invoice.totalAmount().toPlainString(),
            invoice.balance().toPlainString(),
            invoice.status().name());
    }
}
