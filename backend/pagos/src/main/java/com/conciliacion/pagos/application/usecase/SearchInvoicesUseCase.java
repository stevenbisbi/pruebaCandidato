package com.conciliacion.pagos.application.usecase;

import com.conciliacion.pagos.domain.model.InvoiceFilter;
import com.conciliacion.pagos.domain.model.InvoicePage;

/** Puerto de entrada: consulta paginada de facturas con filtros (RF-23). */
public interface SearchInvoicesUseCase {

    int MAX_PAGE_SIZE = 200;

    InvoicePage search(InvoiceFilter filter);
}
