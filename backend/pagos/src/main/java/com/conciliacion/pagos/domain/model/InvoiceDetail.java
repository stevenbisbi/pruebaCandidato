package com.conciliacion.pagos.domain.model;

import java.util.List;

/** Una factura y todos los pagos que recibio, en el orden en que se procesaron. */
public record InvoiceDetail(InvoiceView invoice, List<InvoicePayment> payments) {
}
