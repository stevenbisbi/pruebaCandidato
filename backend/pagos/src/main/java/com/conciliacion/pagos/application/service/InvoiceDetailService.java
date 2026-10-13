package com.conciliacion.pagos.application.service;

import com.conciliacion.pagos.application.usecase.GetInvoiceDetailUseCase;
import com.conciliacion.pagos.application.usecase.NotFoundException;
import com.conciliacion.pagos.domain.model.InvoiceDetail;
import com.conciliacion.pagos.domain.model.InvoiceView;
import com.conciliacion.pagos.domain.repository.InvoiceQueryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Detalle de una factura con sus pagos (Anexo A). */
@Service
public class InvoiceDetailService implements GetInvoiceDetailUseCase {

    private final InvoiceQueryPort invoices;

    public InvoiceDetailService(InvoiceQueryPort invoices) {
        this.invoices = invoices;
    }

    /**
     * Factura y pagos se leen en la misma transaccion: asi el saldo que se muestra corresponde a los
     * pagos que se muestran, aunque un lote este aplicando pagos a esa factura en ese momento.
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public InvoiceDetail getDetail(String number) {
        InvoiceView invoice = invoices.findByNumber(number)
            .orElseThrow(() -> new NotFoundException("La factura " + number + " no existe"));
        return new InvoiceDetail(invoice, invoices.findPayments(number));
    }
}
