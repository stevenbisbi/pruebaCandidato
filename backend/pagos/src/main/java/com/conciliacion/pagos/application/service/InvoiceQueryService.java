package com.conciliacion.pagos.application.service;

import com.conciliacion.pagos.application.port.out.InvoiceQueryPort;
import com.conciliacion.pagos.application.port.out.PaymentLedger;
import com.conciliacion.pagos.application.query.InvoiceFilter;
import com.conciliacion.pagos.application.query.InvoicePage;
import com.conciliacion.pagos.application.query.InvoiceView;
import com.conciliacion.pagos.domain.model.LineResult;

import java.util.List;

public class InvoiceQueryService {

    public static final int MAX_PAGE_SIZE = 200;

    private final InvoiceQueryPort query;
    private final PaymentLedger ledger;

    public InvoiceQueryService(InvoiceQueryPort query, PaymentLedger ledger) {
        this.query = query;
        this.ledger = ledger;
    }

    public InvoicePage search(InvoiceFilter filter) {
        return query.search(filter);
    }

    public InvoiceDetail detail(String number) {
        InvoiceView invoice = query.findByNumber(number)
            .orElseThrow(() -> new NotFoundException("No existe la factura " + number));
        return new InvoiceDetail(invoice, ledger.findByInvoice(number));
    }

    public record InvoiceDetail(InvoiceView invoice, List<LineResult> payments) {
    }
}
