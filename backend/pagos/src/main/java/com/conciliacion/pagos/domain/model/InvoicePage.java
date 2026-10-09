package com.conciliacion.pagos.domain.model;

import java.util.List;

/**
 * Una pagina de facturas.
 *
 * @param hasNext true si hay mas facturas despues de la ultima de esta pagina
 */
public record InvoicePage(List<InvoiceView> items, boolean hasNext) {
}
