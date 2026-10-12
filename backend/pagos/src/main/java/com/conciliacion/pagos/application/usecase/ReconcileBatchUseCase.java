package com.conciliacion.pagos.application.usecase;

/**
 * Puerto de entrada: recibir y aplicar un lote de pagos (RF-16 a RF-21).
 *
 * Es un solo caso de uso para las dos vias de RF-17: el CSV (BatchFileController) y el JSON
 * (BatchPayController) son adaptadores de entrada distintos que llegan aqui con el mismo lote.
 */
public interface ReconcileBatchUseCase {

}
