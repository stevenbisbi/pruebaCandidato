package com.conciliacion.pagos.application.port.out;

import com.conciliacion.pagos.domain.model.Invoice;

import java.util.Collection;
import java.util.Map;

/** Puerto de escritura de facturas usado al aplicar un lote. */
public interface InvoiceRepository {

    /**
     * Carga y bloquea (SELECT ... FOR UPDATE) las facturas indicadas, siempre en el mismo orden
     * para que dos lotes concurrentes no se bloqueen mutuamente (CF-04). Debe llamarse dentro de
     * una transaccion. Las facturas que no existen simplemente no aparecen en el mapa.
     */
    Map<String, Invoice> lockByNumbers(Collection<String> numbers);

    void saveBalances(Collection<Invoice> invoices);
}
