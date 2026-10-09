package com.conciliacion.pagos.adapters.inbound.rest.dto;

import com.conciliacion.pagos.domain.model.Batch;
import com.conciliacion.pagos.domain.model.InvoiceView;
import com.conciliacion.pagos.domain.model.LinePage;
import com.conciliacion.pagos.domain.model.LineResult;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Las respuestas JSON de la API. En el codigo los campos van en ingles y @JsonProperty les pone el
 * nombre en espanol que ve el cliente.
 *
 * Todo monto viaja como texto (por ejemplo "37500483.40") y no como numero: JavaScript lee los
 * numeros JSON como double y podria perder centavos (RF-05).
 */
public final class ApiResponses {

    private ApiResponses() {
    }

    static String money(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2).toPlainString();
    }

    public record BatchResponse(
        @JsonProperty("loteId") String id,
        @JsonProperty("origen") String source,
        @JsonProperty("recibidoEn") Instant receivedAt,
        @JsonProperty("totalLineas") int totalLines,
        @JsonProperty("lineasAplicadas") int appliedLines,
        @JsonProperty("lineasRechazadas") int rejectedLines,
        @JsonProperty("valorAplicado") String appliedAmount,
        @JsonProperty("valorRechazado") String rejectedAmount,
        @JsonProperty("reenvio") boolean replayed) {

        public static BatchResponse of(Batch batch, boolean replayed) {
            return new BatchResponse(batch.id(), batch.source(), batch.receivedAt(), batch.totalLines(),
                batch.appliedLines(), batch.rejectedLines(), money(batch.appliedAmount()),
                money(batch.rejectedAmount()), replayed);
        }
    }

    public record LineResponse(
        @JsonProperty("linea") int lineNumber,
        @JsonProperty("referencia") String reference,
        @JsonProperty("numeroFactura") String invoiceNumber,
        @JsonProperty("valor") String amount,
        @JsonProperty("fechaPago") OffsetDateTime paidAt,
        @JsonProperty("resultado") String outcome,
        @JsonProperty("motivo") String reason,
        @JsonProperty("descripcionMotivo") String reasonDescription,
        @JsonProperty("detalle") String detail,
        @JsonProperty("saldoAnterior") String balanceBefore,
        @JsonProperty("saldoPosterior") String balanceAfter,
        @JsonProperty("estadoFactura") String statusAfter) {

        public static LineResponse of(LineResult r) {
            return new LineResponse(r.lineNumber(), r.reference(), r.invoiceNumber(), money(r.amount()), r.paidAt(),
                r.outcome().name(), r.reason() == null ? null : r.reason().name(),
                r.reason() == null ? null : r.reason().description(), r.detail(), money(r.balanceBefore()),
                money(r.balanceAfter()), r.statusAfter() == null ? null : r.statusAfter().name());
        }
    }

    public record LinePageResponse(
        @JsonProperty("lineas") List<LineResponse> items,
        @JsonProperty("pagina") int page,
        @JsonProperty("tamano") int size,
        @JsonProperty("total") long total) {

        public static LinePageResponse of(LinePage page) {
            List<LineResponse> lines = new ArrayList<>();
            for (LineResult line : page.items()) {
                lines.add(LineResponse.of(line));
            }
            return new LinePageResponse(lines, page.page(), page.size(), page.totalItems());
        }
    }

    public record InvoiceResponse(
        @JsonProperty("numero") String number,
        @JsonProperty("nit") String supplierNit,
        @JsonProperty("razonSocial") String supplierName,
        @JsonProperty("fechaEmision") LocalDate issueDate,
        @JsonProperty("fechaVencimiento") LocalDate dueDate,
        @JsonProperty("valorTotal") String totalAmount,
        @JsonProperty("saldoPendiente") String balance,
        @JsonProperty("estado") String status) {

        public static InvoiceResponse of(InvoiceView v) {
            return new InvoiceResponse(v.number(), v.supplierNit(), v.supplierName(), v.issueDate(), v.dueDate(),
                money(v.totalAmount()), money(v.balance()), v.status().name());
        }
    }

    /** @param nextCursor numero de factura con el que se pide la pagina siguiente, o null si es la ultima */
    public record InvoicePageResponse(
        @JsonProperty("facturas") List<InvoiceResponse> items,
        @JsonProperty("siguienteCursor") String nextCursor) {
    }

    public record ErrorResponse(
        @JsonProperty("codigo") String code,
        @JsonProperty("mensaje") String message) {
    }
}
