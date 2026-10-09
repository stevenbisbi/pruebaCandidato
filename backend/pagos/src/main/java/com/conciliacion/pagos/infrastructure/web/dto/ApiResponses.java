package com.conciliacion.pagos.infrastructure.web.dto;

import com.conciliacion.pagos.application.query.BatchSummary;
import com.conciliacion.pagos.application.query.Dashboard;
import com.conciliacion.pagos.application.query.InvoiceView;
import com.conciliacion.pagos.application.query.LinePage;
import com.conciliacion.pagos.domain.model.LineResult;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Contrato de salida de la API. Todo monto viaja como texto decimal (por ejemplo "37500483.40"):
 * un number de JSON se lee como double en JavaScript y pierde precision por encima de 2^53 (RF-05).
 */
public final class ApiResponses {

    private ApiResponses() {
    }

    static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2).toPlainString();
    }

    public record BatchResponse(
        @JsonProperty("loteId") String id,
        @JsonProperty("origen") String source,
        @JsonProperty("canal") String channel,
        @JsonProperty("fechaGeneracion") LocalDate generatedOn,
        @JsonProperty("recibidoEn") Instant receivedAt,
        @JsonProperty("totalLineas") int totalLines,
        @JsonProperty("lineasAplicadas") int appliedLines,
        @JsonProperty("lineasRechazadas") int rejectedLines,
        @JsonProperty("valorAplicado") String appliedAmount,
        @JsonProperty("valorRechazado") String rejectedAmount,
        @JsonProperty("rechazosPorMotivo") Map<String, Long> rejectionsByReason,
        @JsonProperty("reenvio") boolean replayed) {

        public static BatchResponse of(BatchSummary s, boolean replayed) {
            return new BatchResponse(s.header().id(), s.header().source(), s.header().channel(),
                s.header().generatedOn(), s.header().receivedAt(), s.totals().totalLines(),
                s.totals().appliedLines(), s.totals().rejectedLines(), money(s.totals().appliedAmount()),
                money(s.totals().rejectedAmount()), s.rejectionsByReason(), replayed);
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

        public static LinePageResponse of(LinePage p) {
            return new LinePageResponse(p.items().stream().map(LineResponse::of).toList(), p.page(), p.size(),
                p.totalItems());
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

    /** @param nextCursor cursor opaco para pedir la pagina siguiente, o null si es la ultima */
    public record InvoicePageResponse(
        @JsonProperty("facturas") List<InvoiceResponse> items,
        @JsonProperty("siguienteCursor") String nextCursor) {
    }

    public record InvoiceDetailResponse(
        @JsonProperty("factura") InvoiceResponse invoice,
        @JsonProperty("pagos") List<LineResponse> payments) {
    }

    public record DashboardResponse(
        @JsonProperty("totalFacturado") String totalInvoiced,
        @JsonProperty("cantidadFacturas") long invoiceCount,
        @JsonProperty("saldoPendiente") String totalPending,
        @JsonProperty("totalAplicado") String totalApplied,
        @JsonProperty("cantidadAplicados") long appliedCount,
        @JsonProperty("totalRechazado") String totalRejected,
        @JsonProperty("cantidadRechazados") long rejectedCount,
        @JsonProperty("topProveedores") List<SupplierResponse> topSuppliers) {

        public static DashboardResponse of(Dashboard d) {
            return new DashboardResponse(money(d.totalInvoiced()), d.invoiceCount(), money(d.totalPending()),
                money(d.totalApplied()), d.appliedCount(), money(d.totalRejected()), d.rejectedCount(),
                d.topSuppliers().stream()
                    .map(s -> new SupplierResponse(s.nit(), s.name(), money(s.pendingBalance()))).toList());
        }
    }

    public record SupplierResponse(
        @JsonProperty("nit") String nit,
        @JsonProperty("razonSocial") String name,
        @JsonProperty("saldoPendiente") String pendingBalance) {
    }

    public record ErrorResponse(
        @JsonProperty("codigo") String code,
        @JsonProperty("mensaje") String message) {
    }
}
