package com.conciliacion.pagos.adapters.inbound.rest.dto;

import java.util.List;

/**
 * Cuerpo JSON de un lote (Anexo A). Todos los campos son texto: si un valor o una fecha vienen
 * mal, se rechaza solo esa linea en el dominio (RF-21) y no el lote completo.
 */
public record BatchPayRequestDto(String loteId, String origen, String fechaGeneracion,
                                 List<PaymentDto> pagos) {

    public record PaymentDto(String referencia, String numeroFactura, String valor, String fechaPago) {
    }
}