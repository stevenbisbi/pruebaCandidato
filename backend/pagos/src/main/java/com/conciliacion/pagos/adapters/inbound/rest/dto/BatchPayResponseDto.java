package com.conciliacion.pagos.adapters.inbound.rest.dto;

import com.conciliacion.pagos.domain.model.Batch;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Resumen de un lote procesado. Los montos van como texto para no perder precision (RF-05).
 *
 * @param replayed true si era un reenvio y se devuelve el resultado original sin reaplicar (RF-20)
 */

public record BatchPayResponseDto(
    String id,
    String source,
    String channel,
    LocalDate generatedOn,
    Instant receivedAt,
    int totalLines,
    int appliedLines,
    int rejectedLines,
    String appliedAmount,
    String rejectedAmount,
    boolean replayed) {
        public static BatchPayResponseDto from(Batch batch, boolean replayed) {
        return new BatchPayResponseDto(
            batch.id(),
            batch.source(),
            batch.channel().name(),
            batch.generatedOn(),
            batch.receivedAt(),
            batch.totalLines(),
            batch.appliedLines(),
            batch.rejectedLines(),
            batch.appliedAmount().toPlainString(),
            batch.rejectedAmount().toPlainString(),
            replayed);
        }
    }
    

