package com.conciliacion.pagos.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una factura tal como se muestra en la consulta (RF-01, RF-23). A diferencia de Invoice, que
 * sirve para aplicar pagos, trae el NIT, la razon social y la fecha de emision.
 */
public record InvoiceView(String number, String supplierNit, String supplierName, LocalDate issueDate,
                          LocalDate dueDate, BigDecimal totalAmount, BigDecimal balance, InvoiceStatus status) {
}
