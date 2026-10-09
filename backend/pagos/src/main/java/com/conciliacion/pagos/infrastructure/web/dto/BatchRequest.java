package com.conciliacion.pagos.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Lote en JSON (Anexo A). Los campos de cada pago se reciben como texto a proposito: un valor o
 * una fecha invalida debe rechazar esa linea con su motivo (RF-21), no el lote entero con un 400.
 */
public record BatchRequest(
    @JsonProperty("loteId") @NotBlank @Size(max = 80) String batchId,
    @JsonProperty("origen") @NotBlank @Size(max = 40) String source,
    @JsonProperty("fechaGeneracion") LocalDate generatedOn,
    @JsonProperty("pagos") @NotEmpty @Size(max = 100_000) List<@Valid PaymentRequest> payments) {

    public record PaymentRequest(
        @JsonProperty("referencia") String reference,
        @JsonProperty("numeroFactura") String invoiceNumber,
        @JsonProperty("valor") String amount,
        @JsonProperty("fechaPago") String paidAt) {
    }
}
