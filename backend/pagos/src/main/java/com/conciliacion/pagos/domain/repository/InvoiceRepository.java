package com.conciliacion.pagos.domain.repository;

import com.conciliacion.pagos.domain.model.Invoice;

import java.util.List;
import java.util.Map;

/** Puerto de salida: leer y guardar facturas al aplicar un lote. */
public interface InvoiceRepository {

    /**
     * Carga y bloquea las facturas indicadas hasta que termine la transaccion (CF-04).
     * Devuelve un mapa numero -> factura. Las facturas que no existen no aparecen en el mapa.
     */
    Map<String, Invoice> lockByNumbers(List<String> numbers);

    /** Guarda el saldo y el estado de las facturas que cambiaron. */
    void saveBalances(List<Invoice> invoices);
}
