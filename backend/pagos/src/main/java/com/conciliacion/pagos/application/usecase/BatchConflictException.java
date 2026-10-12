package com.conciliacion.pagos.application.usecase;

/** El id del lote ya existe con un contenido distinto (A-07). Se responde 409. */
public class BatchConflictException extends RuntimeException {
    public BatchConflictException(String batchId) {
        super("El lote " + batchId + " ya existe con un contenido distinto");
    }
}
