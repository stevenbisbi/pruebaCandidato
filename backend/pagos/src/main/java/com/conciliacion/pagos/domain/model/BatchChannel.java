package com.conciliacion.pagos.domain.model;

/** Via por la que llego el lote (RF-17). Los nombres coinciden con la columna payment_batch.channel. */
public enum BatchChannel {
    JSON,
    CSV
}