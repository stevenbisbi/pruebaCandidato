package com.conciliacion.pagos.legacy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de caracterizacion del importador heredado (seccion 8, paso 1).
 *
 * Fijan lo que el modulo hace HOY, incluidos sus defectos, sobre una copia textual del codigo de
 * produccion (solo se le agrego la linea package). No describen el comportamiento deseado: si un
 * caso esta mal, la prueba afirma que esta mal, para saber exactamente que se reemplaza.
 *
 * Los pares "con defecto / sin defecto" (marzo con y sin BOM, abril con y sin las lineas sin
 * fecha) demuestran la causa del sintoma que reporta Cartera, no solo el sintoma.
 */
class ImportadorCsvLegacyCharacterizationTest {

    private static final String HEADER = "referencia;numero_factura;valor;fecha_pago";
    private static final String VALID_LINE = "P-1;FV-1;1000;2026-03-15T10:00:00-05:00";

    private final ImportadorCsvLegacy importer = new ImportadorCsvLegacy();

    @TempDir
    Path tempDir;

    // --- Archivos reales de Tesoreria ---

    @Test
    void rejectsTheWholeMarchFileBecauseOfTheUtf8Bom() throws Exception {
        ResultadoImportacion result = importer.importar(resource("lote-tesoreria-marzo.csv"));

        assertThat(result.fueAceptado()).isFalse();
        assertThat(result.getMotivoRechazo()).startsWith("Encabezado invalido");
        assertThat(result.getPagos()).isEmpty();
    }

    @Test
    void acceptsTheMarchFileOnceTheBomIsRemoved() throws Exception {
        byte[] bytes = Files.readAllBytes(resource("lote-tesoreria-marzo.csv"));
        // El archivo empieza con EF BB BF: se quitan esos 3 bytes y nada mas.
        assertThat(Arrays.copyOf(bytes, 3)).containsExactly(0xEF, 0xBB, 0xBF);
        Path withoutBom = tempDir.resolve("marzo-sin-bom.csv");
        Files.write(withoutBom, Arrays.copyOfRange(bytes, 3, bytes.length));

        ResultadoImportacion result = importer.importar(withoutBom);

        assertThat(result.fueAceptado()).isTrue();
        assertThat(result.getPagos()).hasSize(120);
    }

    @Test
    void rejectsTheWholeAprilFileBecauseThreeLinesHaveAnEmptyLastField() throws Exception {
        ResultadoImportacion result = importer.importar(resource("lote-tesoreria-abril.csv"));

        assertThat(result.fueAceptado()).isFalse();
        // split(";") descarta el campo vacio del final: quedan 3 campos y campos[3] revienta.
        assertThat(result.getMotivoRechazo())
            .isEqualTo("Error procesando el archivo: ArrayIndexOutOfBoundsException"
                + " - Index 3 out of bounds for length 3");
        assertThat(result.getPagos()).isEmpty();
    }

    @Test
    void acceptsTheAprilFileWithoutTheLinesThatHaveNoDate() throws Exception {
        List<String> lines = Files.readAllLines(resource("lote-tesoreria-abril.csv"));
        List<String> withoutEmptyDate = lines.stream()
            .filter(line -> !line.endsWith(";"))
            .collect(Collectors.toList());
        assertThat(lines.size() - withoutEmptyDate.size()).isEqualTo(3);

        ResultadoImportacion result = importer.importar(write(withoutEmptyDate));

        assertThat(result.fueAceptado()).isTrue();
        assertThat(result.getPagos()).hasSize(97);
    }

    // --- Comportamiento general ---

    @Test
    void acceptsAWellFormedFileAndParsesEveryField() throws IOException {
        ResultadoImportacion result = importer.importar(write(List.of(HEADER,
            "P-000001;FV-0042199;37500483;2026-03-15T23:30:00-05:00")));

        assertThat(result.fueAceptado()).isTrue();
        LineaPago payment = result.getPagos().get(0);
        assertThat(payment.getReferencia()).isEqualTo("P-000001");
        assertThat(payment.getNumeroFactura()).isEqualTo("FV-0042199");
        assertThat(payment.getValor()).isEqualTo(37_500_483L);
        assertThat(payment.getFechaPago()).hasToString("2026-03-15T23:30-05:00");
    }

    @Test
    void oneBadLineRejectsTheWholeFile() throws IOException {
        ResultadoImportacion result = importer.importar(write(List.of(HEADER,
            VALID_LINE, "P-2;FV-2;2000", "P-3;FV-3;3000;2026-03-15T10:00:00-05:00")));

        assertThat(result.fueAceptado()).isFalse();
        assertThat(result.getPagos()).isEmpty();
    }

    @Test
    void anAmountWithDecimalsRejectsTheWholeFile() throws IOException {
        ResultadoImportacion result = importer.importar(write(List.of(HEADER,
            VALID_LINE, "P-2;FV-2;100.50;2026-03-15T10:00:00-05:00")));

        assertThat(result.fueAceptado()).isFalse();
        assertThat(result.getMotivoRechazo()).contains("NumberFormatException");
    }

    @Test
    void acceptsZeroAndNegativeAmounts() throws IOException {
        // Defecto: Long.parseLong acepta 0 y negativos, y no hay otra validacion del valor.
        ResultadoImportacion result = importer.importar(write(List.of(HEADER,
            "P-1;FV-1;0;2026-03-15T10:00:00-05:00",
            "P-2;FV-2;-500;2026-03-15T10:00:00-05:00")));

        assertThat(result.fueAceptado()).isTrue();
        assertThat(result.getPagos()).extracting(LineaPago::getValor).containsExactly(0L, -500L);
    }

    @Test
    void aDateWithoutOffsetRejectsTheWholeFile() throws IOException {
        ResultadoImportacion result = importer.importar(write(List.of(HEADER,
            "P-1;FV-1;1000;2026-03-15T10:00:00")));

        assertThat(result.fueAceptado()).isFalse();
        assertThat(result.getMotivoRechazo()).contains("DateTimeParseException");
    }

    @Test
    void rejectsAHeaderWithColumnsInAnotherOrder() throws IOException {
        ResultadoImportacion result = importer.importar(write(List.of(
            "numero_factura;referencia;valor;fecha_pago", VALID_LINE)));

        assertThat(result.fueAceptado()).isFalse();
        assertThat(result.getMotivoRechazo()).startsWith("Encabezado invalido");
    }

    @Test
    void rejectsAnEmptyFile() throws IOException {
        ResultadoImportacion result = importer.importar(write(List.of()));

        assertThat(result.fueAceptado()).isFalse();
        assertThat(result.getMotivoRechazo()).isEqualTo("El archivo esta vacio");
    }

    @Test
    void acceptsAFileWithOnlyTheHeaderAsAnEmptyBatch() throws IOException {
        ResultadoImportacion result = importer.importar(write(List.of(HEADER)));

        assertThat(result.fueAceptado()).isTrue();
        assertThat(result.getPagos()).isEmpty();
    }

    @Test
    void skipsBlankLines() throws IOException {
        ResultadoImportacion result = importer.importar(write(List.of(HEADER,
            VALID_LINE, "", "   ", "P-2;FV-2;2000;2026-03-15T10:00:00-05:00")));

        assertThat(result.fueAceptado()).isTrue();
        assertThat(result.getPagos()).hasSize(2);
    }

    @Test
    void acceptsWindowsLineEndings() throws IOException {
        Path file = tempDir.resolve("crlf.csv");
        Files.writeString(file, HEADER + "\r\n" + VALID_LINE + "\r\n", StandardCharsets.UTF_8);

        ResultadoImportacion result = importer.importar(file);

        assertThat(result.fueAceptado()).isTrue();
        assertThat(result.getPagos()).hasSize(1);
    }

    @Test
    void acceptsExactlyTheLineLimit() throws IOException {
        ResultadoImportacion result = importer.importar(fileWithPayments(100_000, 0));

        assertThat(result.fueAceptado()).isTrue();
        assertThat(result.getPagos()).hasSize(100_000);
    }

    @Test
    void countsBlankLinesAgainstTheLineLimit() throws IOException {
        // Defecto: el limite se mide sobre las lineas del archivo, no sobre los pagos. 100.000 pagos
        // mas una linea en blanco al final superan el limite aunque los pagos no lo superen.
        ResultadoImportacion result = importer.importar(fileWithPayments(100_000, 1));

        assertThat(result.fueAceptado()).isFalse();
        assertThat(result.getMotivoRechazo()).isEqualTo("El archivo supera el limite de 100000 lineas");
    }

    // --- Utilidades ---

    private Path resource(String name) throws URISyntaxException {
        return Path.of(getClass().getResource("/legacy/" + name).toURI());
    }

    private Path write(List<String> lines) throws IOException {
        Path file = Files.createTempFile(tempDir, "lote", ".csv");
        Files.write(file, lines, StandardCharsets.UTF_8);
        return file;
    }

    private Path fileWithPayments(int payments, int trailingBlankLines) throws IOException {
        StringBuilder content = new StringBuilder(HEADER).append('\n');
        for (int i = 1; i <= payments; i++) {
            content.append("P-").append(i).append(";FV-1;1000;2026-03-15T10:00:00-05:00\n");
        }
        content.append("\n".repeat(trailingBlankLines));
        Path file = Files.createTempFile(tempDir, "lote-grande", ".csv");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
