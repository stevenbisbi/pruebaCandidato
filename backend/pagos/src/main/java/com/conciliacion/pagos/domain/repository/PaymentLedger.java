package com.conciliacion.pagos.domain.repository;

import com.conciliacion.pagos.domain.model.LineOutcome;
import com.conciliacion.pagos.domain.model.LinePage;
import com.conciliacion.pagos.domain.model.LineResult;
import com.conciliacion.pagos.domain.model.RejectionReason;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

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
     *
     * @param outcome filtro opcional por resultado; null para todas
     * @param reason filtro opcional por motivo de rechazo (RF-26); null para todos
     */
    LinePage findLines(String batchId, LineOutcome outcome, RejectionReason reason, int page, int size);

    /**
     * Recorre todas las lineas de un lote en orden, entregandolas una a una, sin cargarlas todas en
     * memoria (RF-22: descarga de un lote de hasta 100.000 lineas). Debe llamarse dentro de una
     * transaccion para que la base las envie en tandas.
     */
    void forEachLine(String batchId, Consumer<LineResult> consumer);
}