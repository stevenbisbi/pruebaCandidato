package com.conciliacion.pagos.legacy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de caracterizacion del importador heredado (seccion 8, paso 1).
 *
 * Fijan el comportamiento ACTUAL, incluidos los defectos, sobre una copia textual del modulo
 * (solo se le agrego la declaracion de paquete). No se corrige aqui: el reemplazo es
 * CsvBatchReader, y CsvBatchReaderTest repite estos mismos escenarios con el comportamiento nuevo.
 */
class ImportadorCsvLegacyCharacterizationTest {

    private static final Path SEED = Path.of("..", "..", "seed", "out");
    private static final String HEADER = "referencia;numero_factura;valor;fecha_pago";

    private final ImportadorCsvLegacy importer = new ImportadorCsvLegacy();

    @TempDir
    Path tmp;

    @Test
    void acceptsTheGeneratedFileUsedInDevelopment() {
        ResultadoImportacion result = importer.importar(SEED.resolve("lote-50k.csv"));

        assertThat(result.fueAceptado()).isTrue();
        assertThat(result.getPagos()).hasSize(50_000);
    }

    @Test
    void toleratesWindowsLineEndingsBecauseItTrimsEveryField() throws IOException {
        Path file = write("crlf.csv", HEADER + "\r\nP-1;FV-0000001;100;2026-03-15T10:00:00-05:00\r\n");

        assertThat(importer.importar(file).fueAceptado()).isTrue();
    }

    // --- Defecto 1: BOM UTF-8 -------------------------------------------------------------

    @Test
    void rejectsTheWholeMarchFileBecauseOfTheUtf8Bom() {
        ResultadoImportacion result = importer.importar(SEED.resolve("lote-tesoreria-marzo.csv"));

        assertThat(result.fueAceptado()).isFalse();
        assertThat(result.getMotivoRechazo()).startsWith("Encabezado invalido");
    }

    @Test
    void acceptsTheSameMarchFileOnceTheBomIsRemoved() throws IOException {
        byte[] bytes = Files.readAllBytes(SEED.resolve("lote-tesoreria-marzo.csv"));
        assertThat(bytes).startsWith(0xEF, 0xBB, 0xBF);
        Path withoutBom = tmp.resolve("marzo-sin-bom.csv");
        Files.write(withoutBom, java.util.Arrays.copyOfRange(bytes, 3, bytes.length));

        ResultadoImportacion result = importer.importar(withoutBom);

        assertThat(result.fueAceptado()).isTrue();
        assertThat(result.getPagos()).hasSize(120);
    }

    // --- Defecto 2: campo final vacio -----------------------------------------------------

    @Test
    void rejectsTheWholeAprilFileBecauseThreeLinesHaveAnEmptyLastField() {
        ResultadoImportacion result = importer.importar(SEED.resolve("lote-tesoreria-abril.csv"));

        assertThat(result.fueAceptado()).isFalse();
        // String.split descarta los campos vacios del final: "a;b;c;".split(";") tiene 3 elementos.
        assertThat(result.getMotivoRechazo()).contains("ArrayIndexOutOfBoundsException");
    }

    @Test
    void acceptsTheAprilFileWithoutTheLinesThatHaveNoDate() throws IOException {
        List<String> lines = Files.readAllLines(SEED.resolve("lote-tesoreria-abril.csv"), StandardCharsets.UTF_8);
        Path cleaned = tmp.resolve("abril-limpio.csv");
        Files.write(cleaned, lines.stream().filter(l -> !l.endsWith(";")).toList(), StandardCharsets.UTF_8);

        ResultadoImportacion result = importer.importar(cleaned);

        assertThat(result.fueAceptado()).isTrue();
        assertThat(result.getPagos()).hasSize(97);
    }

    // --- Otros comportamientos fijados -----------------------------------------------------

    @Test
    void oneBadLineRejectsTheWholeFile() throws IOException {
        Path file = write("decimal.csv", HEADER + "\nP-1;FV-1;100;2026-03-15T10:00:00-05:00\nP-2;FV-2;100.50;2026-03-15T10:00:00-05:00\n");

        ResultadoImportacion result = importer.importar(file);

        assertThat(result.fueAceptado()).isFalse();
        assertThat(result.getMotivoRechazo()).contains("NumberFormatException");
        assertThat(result.getPagos()).isEmpty();
    }

    @Test
    void rejectsDatesWithoutOffset() throws IOException {
        Path file = write("sin-zona.csv", HEADER + "\nP-1;FV-1;100;2026-03-15T10:00:00\n");

        assertThat(importer.importar(file).getMotivoRechazo()).contains("DateTimeParseException");
    }

    @Test
    void skipsBlankLinesAndRejectsEmptyFiles() throws IOException {
        Path blanks = write("blancos.csv", HEADER + "\n\nP-1;FV-1;100;2026-03-15T10:00:00-05:00\n   \n");
        Path empty = write("vacio.csv", "");

        assertThat(importer.importar(blanks).getPagos()).hasSize(1);
        assertThat(importer.importar(empty).getMotivoRechazo()).isEqualTo("El archivo esta vacio");
    }

    @Test
    void rejectsFilesThatAreNotUtf8() throws IOException {
        Path file = tmp.resolve("latin1.csv");
        Files.write(file, (HEADER + "\nP-Ñ;FV-1;100;2026-03-15T10:00:00-05:00\n").getBytes(StandardCharsets.ISO_8859_1));

        ResultadoImportacion result = importer.importar(file);

        assertThat(result.fueAceptado()).isFalse();
        assertThat(result.getMotivoRechazo()).contains("UncheckedIOException");
    }

    private Path write(String name, String content) throws IOException {
        Path file = tmp.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
