package com.conciliacion.pagos.domain.repository;

import com.conciliacion.pagos.domain.model.InvoiceFilter;
import com.conciliacion.pagos.domain.model.InvoicePage;

/** Puerto de salida: consulta paginada de facturas (RF-23). */
public interface InvoiceQueryPort {

    /**
     * Facturas que cumplen el filtro, ordenadas por numero. La pagina siguiente se pide con el
     * numero de la ultima factura (D-03): asi no se repiten ni se omiten facturas aunque un lote
     * cambie estados y saldos mientras se navega (PR-03, CF-08).
     */
    InvoicePage search(InvoiceFilter filter);
}
