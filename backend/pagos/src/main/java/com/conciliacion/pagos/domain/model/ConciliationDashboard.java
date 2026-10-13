package com.conciliacion.pagos.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Tablero de conciliacion sobre las facturas con vencimiento en el rango (RF-24, A-08).
 *
 * @param dueFrom inicio del rango de vencimiento; null si no tiene limite inferior
 * @param dueTo fin del rango de vencimiento; null si no tiene limite superior
 * @param totalInvoiced suma del valor total de las facturas del rango
 * @param totalPending suma de su saldo pendiente
 * @param totalApplied suma de los pagos aplicados a esas facturas
 * @param totalRejected suma de los pagos rechazados dirigidos a esas facturas, con valor legible.
 *                      Los rechazos a facturas inexistentes no tienen vencimiento y no entran (A-08).
 * @param topSuppliers los diez proveedores con mayor saldo pendiente en el rango
 */
public record ConciliationDashboard(LocalDate dueFrom, LocalDate dueTo,
                                    long invoiceCount, BigDecimal totalInvoiced, BigDecimal totalPending,
                                    long appliedCount, BigDecimal totalApplied,
                                    long rejectedCount, BigDecimal totalRejected,
                                    List<SupplierBalance> topSuppliers) {
}
