package com.conciliacion.pagos.infrastructure.csv;

import com.conciliacion.pagos.domain.model.BatchLine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lector del CSV de Tesoreria. Reemplaza al importador heredado (ver legacy/ y DECISIONS.md).
 *
 * Diferencias deliberadas con el heredado:
 * - Descarta el BOM UTF-8 que agrega Excel/Bloc de notas al guardar (lote de marzo).
 * - Usa split con limite -1: un campo final vacio sigue contando como columna (lote de abril).
 * - Un error en una linea rechaza esa linea, no el archivo (RF-21). Solo un encabezado invalido
 *   rechaza el archivo completo, porque entonces no hay contrato con el cual interpretar nada.
 * - Lee en streaming y cierra el recurso.
 */
public class CsvBatchReader {

    public static final String[] HEADER = {"referencia", "numero_factura", "valor", "fecha_pago"};
    private static final char BOM = '﻿';
    private static final String SEPARATOR = ";";

    public List<BatchLine> read(InputStream input) {
        var decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, decoder))) {
            String header = reader.readLine();
            if (header == null) {
                throw new InvalidCsvException("El archivo esta vacio");
            }
            validateHeader(header);
            List<BatchLine> lines = new ArrayList<>();
            int lineNumber = 0;
            String raw;
            while ((raw = reader.readLine()) != null) {
                if (raw.isBlank()) {
                    continue;
                }
                lineNumber++;
                lines.add(parse(lineNumber, raw));
            }
            return lines;
        } catch (CharacterCodingException e) {
            throw new InvalidCsvException("El archivo no esta codificado en UTF-8");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void validateHeader(String header) {
        String clean = !header.isEmpty() && header.charAt(0) == BOM ? header.substring(1) : header;
        String[] columns = clean.split(SEPARATOR, -1);
        boolean valid = columns.length == HEADER.length;
        for (int i = 0; valid && i < HEADER.length; i++) {
            valid = columns[i].trim().equalsIgnoreCase(HEADER[i]);
        }
        if (!valid) {
            throw new InvalidCsvException("Encabezado invalido. Se esperaba: " + String.join(SEPARATOR, HEADER));
        }
    }

    private static BatchLine parse(int lineNumber, String raw) {
        String[] fields = raw.split(SEPARATOR, -1);
        if (fields.length != HEADER.length) {
            return BatchLine.malformed(lineNumber,
                "Se esperaban " + HEADER.length + " columnas y llegaron " + fields.length + ": " + abbreviate(raw));
        }
        return BatchLine.of(lineNumber, fields[0].trim(), fields[1].trim(), fields[2].trim(), fields[3].trim());
    }

    private static String abbreviate(String raw) {
        return raw.length() <= 120 ? raw : raw.substring(0, 120) + "...";
    }

    public static class InvalidCsvException extends RuntimeException {
        public InvalidCsvException(String message) {
            super(message);
        }
    }
}
