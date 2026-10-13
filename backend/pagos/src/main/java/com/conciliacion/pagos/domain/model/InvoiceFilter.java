package com.conciliacion.pagos.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Filtros de la consulta de facturas (RF-23). Todos son opcionales y se combinan con AND: null o
 * vacio significa "sin filtro".
 *
 * @param statuses uno o varios estados; la factura debe estar en alguno de ellos
 * @param dueFrom vencimiento desde (inclusive)
 * @param dueTo vencimiento hasta (inclusive)
 * @param minBalance saldo pendiente desde (inclusive)
 * @param maxBalance saldo pendiente hasta (inclusive)
 * @param afterNumber cursor de paginacion (PR-03): numero de la ultima factura de la pagina anterior
 * @param size cuantas facturas devolver
 */
public record InvoiceFilter(String supplierNit, Set<InvoiceStatus> statuses, LocalDate dueFrom, LocalDate dueTo,
                            BigDecimal minBalance, BigDecimal maxBalance, String afterNumber, int size) {
}
