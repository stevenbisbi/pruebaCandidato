package com.conciliacion.pagos.application.service;

/** Se recibio un lote con un identificador ya usado pero con contenido distinto. */
public class BatchConflictException extends RuntimeException {

    public BatchConflictException(String batchId) {
        super("El lote " + batchId + " ya fue recibido con un contenido distinto. "
            + "Un reenvio debe ser identico; un lote nuevo necesita un identificador nuevo.");
    }
}
