package com.conciliacion.pagos.application.port.out;

import com.conciliacion.pagos.application.query.BatchHeader;
import com.conciliacion.pagos.application.query.BatchSummary;
import com.conciliacion.pagos.application.query.BatchTotals;

import java.util.Optional;

public interface BatchRepository {

    /**
     * Registra el lote si su identificador no existe. Devuelve false si ya existia. Si otra
     * transaccion esta registrando el mismo identificador, la llamada espera a que termine: es lo
     * que convierte el reenvio concurrente de Tesoreria (RF-20) en una operacion segura.
     */
    boolean register(BatchHeader header);

    Optional<BatchHeader> findHeader(String batchId);

    void complete(String batchId, BatchTotals totals);

    Optional<BatchSummary> findSummary(String batchId);
}
