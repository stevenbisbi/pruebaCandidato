package com.conciliacion.pagos.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una factura para mostrar en la consulta, con la razon social de su proveedor. */
public record InvoiceView(String number, String supplierNit, String supplierName, LocalDate issueDate,
                          LocalDate dueDate, BigDecimal totalAmount, BigDecimal balance, InvoiceStatus status) {
}
