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
 * Lee el CSV de Tesoreria (referencia;numero_factura;valor;fecha_pago) y lo convierte en lineas
 * de lote. Reemplaza al importador heredado (seccion 8, DECISIONS.md D-07).
 *
 * Diferencias con el heredado:
 * - Quita el BOM UTF-8 que agregan Excel o el Bloc de notas (defecto 1).
 * - Usa split(";", -1) para conservar los campos vacios del final (defecto 2).
 * - Una linea mala se marca sola y el resto del lote sigue (RF-21). Solo un archivo vacio, un
 *   encabezado distinto o un exceso de lineas rechazan el archivo completo.
 * - No interpreta valores ni fechas: los entrega como texto y los valida el dominio
 *   (BatchReconciler), que es donde viven las reglas de RF-10 y A-02.
 * - Lee por streaming en lugar de cargar el archivo completo en memoria.
 */
@Component
public class CsvBatchReader {

    static final int MAX_LINES = 100_000;

    private static final String SEPARATOR = ";";
    private static final String[] EXPECTED_HEADER = {"referencia", "numero_factura", "valor", "fecha_pago"};
    private static final char BOM = '﻿';
    private static final char REPLACEMENT_CHAR = '�';

    /**
     * @return las lineas del lote; lineNumber es la posicion del pago dentro del lote (1-based, sin
     *         contar encabezado ni lineas en blanco), igual que el indice de un lote JSON
     * @throws InvalidCsvException si el archivo no se puede tratar como lote
     */
    public List<BatchLine> read(InputStream input) throws IOException {
        // InputStreamReader no lanza excepcion ante bytes que no son UTF-8: los cambia por U+FFFD,
        // y la linea que los contenga se marca como mal formada en toLine.
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                throw new InvalidCsvException("El archivo esta vacio");
            }
            if (!isExpectedHeader(stripBom(header))) {
                throw new InvalidCsvException(
                    "Encabezado invalido. Se esperaba: " + String.join(SEPARATOR, EXPECTED_HEADER));
            }

            List<BatchLine> lines = new ArrayList<>();
            String raw;
            while ((raw = reader.readLine()) != null) {
                if (raw.isBlank()) {
                    continue;
                }
                // El limite cuenta pagos, no lineas del archivo: las lineas en blanco no suman.
                if (lines.size() == MAX_LINES) {
                    throw new InvalidCsvException("El archivo supera el limite de " + MAX_LINES + " pagos");
                }
                lines.add(toLine(lines.size() + 1, raw));
            }
            if (lines.isEmpty()) {
                throw new InvalidCsvException("El archivo no contiene pagos");
            }
            return lines;
        }
    }

    private BatchLine toLine(int lineNumber, String raw) {
        if (raw.indexOf(REPLACEMENT_CHAR) >= 0) {
            return BatchLine.malformed(lineNumber, "La linea tiene caracteres que no son UTF-8");
        }
        // -1: "a;b;c;" da 4 campos (el ultimo vacio) y no 3. Era el defecto 2 del heredado.
        String[] fields = raw.split(SEPARATOR, -1);
        if (fields.length != EXPECTED_HEADER.length) {
            return BatchLine.malformed(lineNumber,
                "Se esperaban " + EXPECTED_HEADER.length + " columnas y llegaron " + fields.length);
        }
        return BatchLine.of(lineNumber, fields[0].trim(), fields[1].trim(), fields[2].trim(), fields[3].trim());
    }

    private String stripBom(String header) {
        if (!header.isEmpty() && header.charAt(0) == BOM) {
            return header.substring(1);
        }
        return header;
    }

    private boolean isExpectedHeader(String header) {
        String[] columns = header.split(SEPARATOR, -1);
        if (columns.length != EXPECTED_HEADER.length) {
            return false;
        }
        for (int i = 0; i < EXPECTED_HEADER.length; i++) {
            if (!columns[i].trim().equals(EXPECTED_HEADER[i])) {
                return false;
            }
        }
        return true;
    }
}
