package com.conciliacion.pagos.application.query;

import com.conciliacion.pagos.domain.model.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Filtros de RF-23. Todos opcionales y combinables.
 *
 * @param afterDueDate cursor de paginacion por llave (PR-03): ultima fecha de vencimiento vista
 * @param afterNumber cursor de paginacion por llave: ultimo numero de factura visto
 */
public record InvoiceFilter(String supplierNit, Set<InvoiceStatus> statuses, LocalDate dueFrom, LocalDate dueTo,
                            BigDecimal balanceMin, BigDecimal balanceMax, LocalDate afterDueDate,
                            String afterNumber, int size) {
}
