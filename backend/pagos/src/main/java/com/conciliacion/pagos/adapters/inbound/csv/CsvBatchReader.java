package com.conciliacion.pagos.adapters.inbound.csv;

import com.conciliacion.pagos.domain.model.BatchLine;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lee el CSV de Tesoreria: referencia;numero_factura;valor;fecha_pago
 *
 * Reemplaza al importador heredado (carpeta legacy/) y corrige sus dos defectos:
 * 1. Quita el BOM que Excel o el Bloc de notas agregan al inicio del archivo (lote de marzo).
 * 2. Usa split(";", -1) para que un campo final vacio siga contando como columna (lote de abril).
 *
 * Ademas, una linea mala se marca como mal formada y se rechaza sola; no tumba el archivo (RF-21).
 * Solo un encabezado invalido rechaza el archivo entero.
 */
@Component
public class CsvBatchReader {

    private static final String EXPECTED_HEADER = "referencia;numero_factura;valor;fecha_pago";
    private static final char BOM = (char) 0xFEFF;

    public List<BatchLine> read(InputStream input) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        try {
            String header = reader.readLine();
            if (header == null) {
                throw new InvalidCsvException("El archivo esta vacio");
            }
            if (!header.isEmpty() && header.charAt(0) == BOM) {
                header = header.substring(1);
            }
            if (!header.trim().equalsIgnoreCase(EXPECTED_HEADER)) {
                throw new InvalidCsvException("Encabezado invalido. Se esperaba: " + EXPECTED_HEADER);
            }

            List<BatchLine> lines = new ArrayList<>();
            int lineNumber = 0;
            String text = reader.readLine();
            while (text != null) {
                if (!text.isBlank()) {
                    lineNumber++;
                    lines.add(toBatchLine(lineNumber, text));
                }
                text = reader.readLine();
            }
            return lines;
        } finally {
            reader.close();
        }
    }

    private BatchLine toBatchLine(int lineNumber, String text) {
        // El -1 hace que "a;b;c;" de 4 campos (el ultimo vacio) y no 3, que era el bug del heredado.
        String[] fields = text.split(";", -1);
        if (fields.length != 4) {
            return BatchLine.malformed(lineNumber, "Se esperaban 4 columnas y llegaron " + fields.length);
        }
        return BatchLine.of(lineNumber, fields[0].trim(), fields[1].trim(), fields[2].trim(), fields[3].trim());
    }

    /** El archivo no se puede leer: vacio o con un encabezado distinto al acordado. */
    public static class InvalidCsvException extends RuntimeException {
        public InvalidCsvException(String message) {
            super(message);
        }
    }
}
