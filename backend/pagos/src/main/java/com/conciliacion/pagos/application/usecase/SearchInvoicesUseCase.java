package com.conciliacion.pagos.application.usecase;

import com.conciliacion.pagos.domain.model.InvoiceFilter;
import com.conciliacion.pagos.domain.model.InvoicePage;

/** Puerto de entrada: consulta paginada de facturas con filtros combinables (RF-23). */
public interface SearchInvoicesUseCase {

    int DEFAULT_PAGE_SIZE = 50;
    int MAX_PAGE_SIZE = 200;

    /**
     * Un tamano fuera de 1..MAX_PAGE_SIZE se ajusta al limite mas cercano.
     *
     * @throws InvalidRequestException si un rango esta invertido (desde mayor que hasta)
     */
    InvoicePage search(InvoiceFilter filter);
}
