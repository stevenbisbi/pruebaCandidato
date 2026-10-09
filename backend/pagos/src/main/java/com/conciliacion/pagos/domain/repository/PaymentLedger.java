package com.conciliacion.pagos.domain.repository;

import com.conciliacion.pagos.domain.model.LinePage;
import com.conciliacion.pagos.domain.model.LineResult;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Puerto de salida: resultado de cada linea, que es tambien la auditoria (RF-15, RF-21). */
public interface PaymentLedger {

    /** De las referencias dadas, devuelve las que ya se aplicaron en algun lote (RF-12). */
    Set<String> findAppliedReferences(List<String> references);

    /** Guarda el resultado de todas las lineas de un lote. */
    void record(String batchId, List<LineResult> results, String user, Instant processedAt);

    /** Una pagina del resultado de un lote. */
    LinePage findLines(String batchId, int page, int size);
}
