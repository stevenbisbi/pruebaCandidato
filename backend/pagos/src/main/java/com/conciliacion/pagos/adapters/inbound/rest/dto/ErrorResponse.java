package com.conciliacion.pagos.adapters.inbound.rest.dto;

public record ErrorResponse(int status, String error, String message) {
}
