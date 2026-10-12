package com.conciliacion.pagos.adapters.inbound.csv;

import com.conciliacion.pagos.domain.model.BatchLine;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas del lector que reemplaza al importador heredado (seccion 8, paso 2). Los dos primeros
 * casos usan los mismos archivos reales que ImportadorCsvLegacyCharacterizationTest: alli se fija
 * que el heredado los rechaza completos; aqui, que el lector nuevo los lee linea por linea.
 */
class CsvBatchReaderTest {

    private static final String HEADER = "referencia;numero_factura;valor;fecha_pago";
    private static final String VALID_LINE = "P-1;FV-1;1000;2026-03-15T10:00:00-05:00";

    private final CsvBatchReader reader = new CsvBatchReader();

    // --- Archivos reales de Tesoreria: los que el heredado rechazaba completos ---

    @Test
    void readsEveryLineOfTheMarchFileDespiteTheBom() throws IOException {
        List<BatchLine> lines = reader.read(resource("lote-tesoreria-marzo.csv"));

        assertThat(lines).hasSize(120);
        assertThat(lines).allSatisfy(line -> assertThat(line.formatError()).isNull());
        // Sin el BOM pegado: la primera referencia se lee limpia.
        assertThat(lines.get(0).reference()).isEqualTo("TES-MAR-000001");
    }

    @Test
    void readsEveryLineOfTheAprilFileAndLeavesTheEmptyDatesToTheDomain() throws IOException {
        List<BatchLine> lines = reader.read(resource("lote-tesoreria-abril.csv"));

        assertThat(lines).hasSize(100);
        assertThat(lines).allSatisfy(line -> assertThat(line.formatError()).isNull());
        // Las 3 lineas sin fecha llegan con la fecha vacia: el dominio las rechaza una por una
        // (FECHA_INVALIDA) y las otras 97 se procesan.
        assertThat(lines).filteredOn(line -> line.rawPaidAt().isEmpty())
            .extracting(BatchLine::reference)
            .containsExactly("TES-ABR-000013", "TES-ABR-000048", "TES-ABR-000084");
    }

    // --- Una linea mala no tumba el lote ---

    @Test
    void marksALineWithMissingColumnsAndKeepsTheRest() throws IOException {
        List<BatchLine> lines = reader.read(csv(HEADER, VALID_LINE, "P-2;FV-2;2000", VALID_LINE));

        assertThat(lines).hasSize(3);
        assertThat(lines.get(0).formatError()).isNull();
        assertThat(lines.get(1).formatError()).isEqualTo("Se esperaban 4 columnas y llegaron 3");
        assertThat(lines.get(2).formatError()).isNull();
    }

    @Test
    void marksALineWithExtraColumns() throws IOException {
        List<BatchLine> lines = reader.read(csv(HEADER, VALID_LINE + ";sobra"));

        assertThat(lines.get(0).formatError()).isEqualTo("Se esperaban 4 columnas y llegaron 5");
    }

    @Test
    void marksALineThatIsNotUtf8() throws IOException {
        byte[] latin1Line = "P-Ñ;FV-1;1000;2026-03-15T10:00:00-05:00\n".getBytes(StandardCharsets.ISO_8859_1);
        byte[] header = (HEADER + "\n" + VALID_LINE + "\n").getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[header.length + latin1Line.length];
        System.arraycopy(header, 0, content, 0, header.length);
        System.arraycopy(latin1Line, 0, content, header.length, latin1Line.length);

        List<BatchLine> lines = reader.read(new ByteArrayInputStream(content));

        assertThat(lines.get(0).formatError()).isNull();
        assertThat(lines.get(1).formatError()).isEqualTo("La linea tiene caracteres que no son UTF-8");
    }

    @Test
    void keepsValuesAsTextForTheDomainToValidate() throws IOException {
        // El heredado tumbaba el archivo con NumberFormatException; aqui el valor llega tal cual y
        // el dominio decide (A-02: los pagos con decimales se redondean al peso).
        List<BatchLine> lines = reader.read(csv(HEADER, " P-1 ; FV-1 ; 100.50 ; 2026-03-15T10:00:00 "));

        BatchLine line = lines.get(0);
        assertThat(line.formatError()).isNull();
        assertThat(line.reference()).isEqualTo("P-1");
        assertThat(line.invoiceNumber()).isEqualTo("FV-1");
        assertThat(line.rawAmount()).isEqualTo("100.50");
        assertThat(line.rawPaidAt()).isEqualTo("2026-03-15T10:00:00");
    }

    // --- Numeracion, lineas en blanco y saltos de linea ---

    @Test
    void numbersPaymentsByPositionSkippingBlankLines() throws IOException {
        List<BatchLine> lines = reader.read(csv(HEADER, VALID_LINE, "", "   ", "P-2;FV-2;2000;2026-03-15T10:00:00-05:00"));

        assertThat(lines).extracting(BatchLine::lineNumber).containsExactly(1, 2);
    }

    @Test
    void acceptsWindowsLineEndings() throws IOException {
        List<BatchLine> lines = reader.read(stream(HEADER + "\r\n" + VALID_LINE + "\r\n"));

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).rawPaidAt()).isEqualTo("2026-03-15T10:00:00-05:00");
    }

    // --- Lo unico que rechaza el archivo completo ---

    @Test
    void rejectsAnEmptyFile() {
        assertThatThrownBy(() -> reader.read(stream("")))
            .isInstanceOf(InvalidCsvException.class)
            .hasMessage("El archivo esta vacio");
    }

    @Test
    void rejectsAFileWithOnlyTheHeader() {
        // Cambio respecto al heredado, que lo aceptaba como lote vacio: un lote sin pagos no
        // tiene nada que conciliar.
        assertThatThrownBy(() -> reader.read(csv(HEADER)))
            .isInstanceOf(InvalidCsvException.class)
            .hasMessage("El archivo no contiene pagos");
    }

    @Test
    void rejectsAnUnexpectedHeader() {
        assertThatThrownBy(() -> reader.read(csv("numero_factura;referencia;valor;fecha_pago", VALID_LINE)))
            .isInstanceOf(InvalidCsvException.class)
            .hasMessageStartingWith("Encabezado invalido");
    }

    @Test
    void acceptsExactlyTheLimitEvenWithTrailingBlankLines() throws IOException {
        // El heredado contaba las lineas en blanco contra el limite; aqui solo cuentan los pagos.
        List<BatchLine> lines = reader.read(stream(fileWithPayments(CsvBatchReader.MAX_LINES) + "\n\n"));

        assertThat(lines).hasSize(CsvBatchReader.MAX_LINES);
    }

    @Test
    void rejectsMorePaymentsThanTheLimit() {
        assertThatThrownBy(() -> reader.read(stream(fileWithPayments(CsvBatchReader.MAX_LINES + 1))))
            .isInstanceOf(InvalidCsvException.class)
            .hasMessage("El archivo supera el limite de 100000 pagos");
    }

    // --- Utilidades ---

    private InputStream resource(String name) {
        return getClass().getResourceAsStream("/legacy/" + name);
    }

    private InputStream csv(String... lines) {
        return stream(String.join("\n", lines) + "\n");
    }

    private InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private String fileWithPayments(int payments) {
        StringBuilder content = new StringBuilder(HEADER).append('\n');
        for (int i = 1; i <= payments; i++) {
            content.append("P-").append(i).append(";FV-1;1000;2026-03-15T10:00:00-05:00\n");
        }
        return content.toString();
    }
}
