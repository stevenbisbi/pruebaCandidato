package com.conciliacion.pagos.adapters.inbound.rest.dto;

import com.conciliacion.pagos.application.dto.IncomingBatch;
import com.conciliacion.pagos.domain.model.BatchChannel;
import com.conciliacion.pagos.domain.model.BatchLine;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Cuerpo JSON de un lote (Anexo A). Los campos de cada pago son texto: si un valor o una fecha
 * vienen mal, se rechaza solo esa linea en el dominio (RF-21) y no el lote completo.
 */
public record BatchPayRequestDto(String loteId, String origen, LocalDate fechaGeneracion,
                                 @NotEmpty(message = "El lote no contiene pagos") List<PaymentDto> pagos) {

    public record PaymentDto(String referencia, String numeroFactura, String valor, String fechaPago) {
    }

    /** Convierte el JSON en el lote que recibe el caso de uso, numerando los pagos por posicion. */
    public IncomingBatch toIncomingBatch() {
        List<BatchLine> lines = new ArrayList<>();
        for (int i = 0; i < pagos.size(); i++) {
            PaymentDto p = pagos.get(i);
            if (p == null) {
                lines.add(BatchLine.malformed(i + 1, "Pago vacio"));
            } else {
                lines.add(BatchLine.of(i + 1, p.referencia(), p.numeroFactura(), p.valor(), p.fechaPago()));
            }
        }
        String source = (origen == null || origen.isBlank()) ? "TESORERIA" : origen;
        return new IncomingBatch(loteId, source, BatchChannel.JSON, fechaGeneracion, lines);
    }
}