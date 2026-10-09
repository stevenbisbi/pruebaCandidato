package com.conciliacion.pagos.application.service;

import com.conciliacion.pagos.application.usecase.SearchInvoicesUseCase;
import com.conciliacion.pagos.domain.model.InvoiceFilter;
import com.conciliacion.pagos.domain.model.InvoicePage;
import com.conciliacion.pagos.domain.repository.InvoiceQueryPort;
import org.springframework.stereotype.Service;

/** Consulta paginada de facturas (RF-23). */
@Service
public class InvoiceQueryService implements SearchInvoicesUseCase {

    private final InvoiceQueryPort query;

    public InvoiceQueryService(InvoiceQueryPort query) {
        this.query = query;
    }

    @Override
    public InvoicePage search(InvoiceFilter filter) {
        return query.search(filter);
    }
}
