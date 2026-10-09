package com.conciliacion.pagos.domain.repository;

import com.conciliacion.pagos.domain.model.InvoiceFilter;
import com.conciliacion.pagos.domain.model.InvoicePage;

public interface InvoiceQueryPort {

    InvoicePage search(InvoiceFilter filter);
}
