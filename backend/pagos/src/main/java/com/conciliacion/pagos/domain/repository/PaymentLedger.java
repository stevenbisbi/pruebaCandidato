package com.conciliacion.pagos.domain.repository;

import com.conciliacion.pagos.domain.model.LineResult;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Puerto de salida: el resultado de cada linea, que es tambien la auditoria (RF-15, RF-21). */
public interface PaymentLedger {

    /** De las referencias dadas, devuelve las que ya se aplicaron en algun lote (RF-12, A-05). */
    Set<String> findAppliedReferences(Collection<String> references);

    /** Guarda el resultado de todas las lineas de un lote, con el usuario y el instante (RF-15). */
    void record(String batchId, List<LineResult> results, String user, Instant processedAt);

    /**
     * Una pagina del resultado de un lote, ordenada por numero de linea (RF-22). Aqui sirve la
     * paginacion por numero de pagina: el resultado de un lote no cambia despues de procesarlo,
     * asi que no hay riesgo de saltar o repetir filas como en las facturas (PR-03).
     */
    List<LineResult> findLines(String batchId, int page, int size);
}