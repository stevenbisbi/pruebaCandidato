package com.conciliacion.pagos.domain.model;

import java.util.List;

/**
 * Filtros de la consulta de facturas (RF-23). Todos son opcionales: null o vacio = sin filtro.
 *
 * @param afterNumber paginacion (PR-03): numero de la ultima factura de la pagina anterior
 * @param size cuantas facturas devolver
 */
public record InvoiceFilter(String supplierNit, List<InvoiceStatus> statuses, String afterNumber, int size) {
}
