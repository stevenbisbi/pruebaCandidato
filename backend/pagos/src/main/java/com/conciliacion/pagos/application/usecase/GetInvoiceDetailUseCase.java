package com.conciliacion.pagos.application.usecase;

import com.conciliacion.pagos.domain.model.InvoiceDetail;

/** Puerto de entrada: una factura y los pagos que recibio, con su auditoria (Anexo A, RF-15). */
public interface GetInvoiceDetailUseCase {

    /** @throws NotFoundException si la factura no existe */
    InvoiceDetail getDetail(String number);
}
