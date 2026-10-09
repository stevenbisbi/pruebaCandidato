package com.conciliacion.pagos.application.port.out;

import com.conciliacion.pagos.application.query.InvoiceFilter;
import com.conciliacion.pagos.application.query.InvoicePage;
import com.conciliacion.pagos.application.query.InvoiceView;

import java.util.Optional;

public interface InvoiceQueryPort {

    InvoicePage search(InvoiceFilter filter);

    Optional<InvoiceView> findByNumber(String number);
}
