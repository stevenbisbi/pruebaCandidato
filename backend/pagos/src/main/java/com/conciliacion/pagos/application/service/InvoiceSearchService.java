package com.conciliacion.pagos.application.service;

import com.conciliacion.pagos.application.usecase.InvalidRequestException;
import com.conciliacion.pagos.application.usecase.SearchInvoicesUseCase;
import com.conciliacion.pagos.domain.model.InvoiceFilter;
import com.conciliacion.pagos.domain.model.InvoicePage;
import com.conciliacion.pagos.domain.repository.InvoiceQueryPort;
import org.springframework.stereotype.Service;

import java.util.Set;

/** Consulta de facturas (RF-23): valida y normaliza el filtro, y delega en el puerto. */
@Service
public class InvoiceSearchService implements SearchInvoicesUseCase {

    private final InvoiceQueryPort invoices;

    public InvoiceSearchService(InvoiceQueryPort invoices) {
        this.invoices = invoices;
    }

    @Override
    public InvoicePage search(InvoiceFilter filter) {
        if (filter.dueFrom() != null && filter.dueTo() != null && filter.dueFrom().isAfter(filter.dueTo())) {
            throw new InvalidRequestException("El vencimiento desde no puede ser posterior al vencimiento hasta");
        }
        if (filter.minBalance() != null && filter.maxBalance() != null
            && filter.minBalance().compareTo(filter.maxBalance()) > 0) {
            throw new InvalidRequestException("El saldo minimo no puede ser mayor que el saldo maximo");
        }
        // Un texto vacio en la URL (?nit=) cuenta como "sin filtro", no como "NIT vacio".
        InvoiceFilter normalized = new InvoiceFilter(
            blankToNull(filter.supplierNit()),
            filter.statuses() == null ? Set.of() : filter.statuses(),
            filter.dueFrom(), filter.dueTo(),
            filter.minBalance(), filter.maxBalance(),
            blankToNull(filter.afterNumber()),
            Math.clamp(filter.size(), 1, MAX_PAGE_SIZE));
        return invoices.search(normalized);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
