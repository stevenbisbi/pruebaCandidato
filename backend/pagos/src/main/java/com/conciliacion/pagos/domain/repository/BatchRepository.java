package com.conciliacion.pagos.domain.repository;

import com.conciliacion.pagos.domain.model.Batch;

import java.time.Instant;

/** Puerto de salida: guardar y leer lotes. */
public interface BatchRepository {

    /**
     * Registra el lote si su id no existe y devuelve true. Si ya existia devuelve false.
     * Si otro proceso esta registrando el mismo id en ese momento, esta llamada espera a que
     * termine: asi dos reenvios simultaneos del mismo lote no se aplican dos veces (RF-20).
     */
    boolean insert(String id, String source, String contentHash, Instant receivedAt);

    /** Devuelve el lote, o null si no existe. */
    Batch find(String id);

    /** Guarda los totales del lote ya procesado. */
    void saveTotals(Batch batch);
}
