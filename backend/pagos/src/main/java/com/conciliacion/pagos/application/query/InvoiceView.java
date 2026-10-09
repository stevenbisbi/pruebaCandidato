package com.conciliacion.pagos.application.query;

import com.conciliacion.pagos.domain.model.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceView(String number, String supplierNit, String supplierName, LocalDate issueDate,
                          LocalDate dueDate, BigDecimal totalAmount, BigDecimal balance, InvoiceStatus status) {
}
