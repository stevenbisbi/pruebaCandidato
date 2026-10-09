package com.conciliacion.pagos.domain.model;

/** Motivos de rechazo de una linea de pago (RF-08, RF-09, RF-10, RF-12, RF-21). */
public enum RejectionReason {
    FORMATO_INVALIDO("La linea no tiene el formato esperado"),
    VALOR_INVALIDO("El valor debe ser un entero positivo en pesos"),
    FECHA_INVALIDA("La fecha de pago falta o no tiene formato ISO-8601 con zona"),
    REFERENCIA_DUPLICADA("La referencia de pago ya fue aplicada"),
    FACTURA_INEXISTENTE("La factura destino no existe"),
    EXCEDE_SALDO("El valor del pago excede el saldo pendiente de la factura");

    private final String description;

    RejectionReason(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
