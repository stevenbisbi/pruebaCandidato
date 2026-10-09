package com.conciliacion.pagos.legacy;

import java.time.OffsetDateTime;

/** Una linea de pago tal como la entrega Tesoreria en el archivo plano. */
public class LineaPago {

    private final String referencia;
    private final String numeroFactura;
    private final long valor;
    private final OffsetDateTime fechaPago;

    public LineaPago(String referencia, String numeroFactura, long valor, OffsetDateTime fechaPago) {
        this.referencia = referencia;
        this.numeroFactura = numeroFactura;
        this.valor = valor;
        this.fechaPago = fechaPago;
    }

    public String getReferencia() {
        return referencia;
    }

    public String getNumeroFactura() {
        return numeroFactura;
    }

    public long getValor() {
        return valor;
    }

    public OffsetDateTime getFechaPago() {
        return fechaPago;
    }

    @Override
    public String toString() {
        return referencia + " -> " + numeroFactura + " por " + valor + " el " + fechaPago;
    }
}
