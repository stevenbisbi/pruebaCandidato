package com.conciliacion.pagos.adapters.inbound.rest.dto;

/** Cuantas facturas cumplen los filtros de la consulta (RF-23), para numerar las paginas. */
public record InvoiceCountResponseDto(long totalItems) {
}
