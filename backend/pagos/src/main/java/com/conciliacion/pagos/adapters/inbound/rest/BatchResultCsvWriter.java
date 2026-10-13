package com.conciliacion.pagos.adapters.inbound.rest;

import com.conciliacion.pagos.domain.model.LineResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.function.Consumer;

/**
 * Escribe el resultado de un lote en CSV (RF-22, A-09): el mismo separador que usa Tesoreria (';'),
 * una fila por linea y los montos con punto decimal, sin separador de miles. El formato colombiano
 * (RF-28) es solo de presentacion en pantalla.
 */
public class BatchResultCsvWriter implements Consumer<LineResult> {

    static final String HEADER = "linea;referencia;numero_factura;valor;fecha_pago;resultado;motivo;detalle;"
        + "saldo_anterior;saldo_posterior;estado_factura;ajuste_redondeo";

    private final Writer out;

    public BatchResultCsvWriter(Writer out) {
        this.out = out;
        write(HEADER);
    }

    @Override
    public void accept(LineResult line) {
        write(String.join(";",
            String.valueOf(line.lineNumber()),
            field(line.reference()),
            field(line.invoiceNumber()),
            money(line.amount()),
            line.paidAt() == null ? "" : line.paidAt().toString(),
            line.outcome().name(),
            line.reason() == null ? "" : line.reason().name(),
            field(line.detail()),
            money(line.balanceBefore()),
            money(line.balanceAfter()),
            line.statusAfter() == null ? "" : line.statusAfter().name(),
            money(line.roundingAdjustment())));
    }

    private void write(String row) {
        try {
            out.write(row);
            out.write("\r\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String money(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    /**
     * Un texto que trae el separador, comillas o saltos de linea va entre comillas, con las comillas
     * internas duplicadas (RFC 4180). Una referencia que llego por JSON puede traer un ';'.
     */
    static String field(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(";") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
