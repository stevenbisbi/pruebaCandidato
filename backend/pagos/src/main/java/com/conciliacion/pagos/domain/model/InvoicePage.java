package com.conciliacion.pagos.domain.model;

import java.util.List;

/**
 * Una pagina de la consulta de facturas (RF-23).
 *
 * @param nextCursor numero de factura con el que se pide la pagina siguiente; null si no hay mas
 */
public record InvoicePage(List<InvoiceView> items, String nextCursor) {
}
