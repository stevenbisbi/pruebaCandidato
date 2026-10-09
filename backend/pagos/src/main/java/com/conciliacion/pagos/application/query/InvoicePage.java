package com.conciliacion.pagos.application.query;

import java.util.List;

/** @param hasNext si existe al menos una factura despues de la ultima de esta pagina */
public record InvoicePage(List<InvoiceView> items, boolean hasNext) {
}
