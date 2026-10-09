package com.conciliacion.pagos.adapters.inbound.csv;

import com.conciliacion.pagos.domain.model.BatchLine;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Los mismos escenarios de la caracterizacion del heredado, con el comportamiento corregido. */
class CsvBatchReaderTest {

    private static final Path SEED = Path.of("..", "..", "seed", "out");
    private final CsvBatchReader reader = new CsvBatchReader();

    @Test
    void readsTheMarchFileDespiteTheBom() throws IOException {
        List<BatchLine> lines = read(SEED.resolve("lote-tesoreria-marzo.csv"));

        assertThat(lines).hasSize(120);
        assertThat(lines.getFirst().reference()).isEqualTo("TES-MAR-000001");
        assertThat(lines).allMatch(l -> l.formatError() == null);
    }

    @Test
    void readsTheAprilFileKeepingTheLinesWithoutDateAsIndividualLines() throws IOException {
        List<BatchLine> lines = read(SEED.resolve("lote-tesoreria-abril.csv"));

        assertThat(lines).hasSize(100);
        assertThat(lines).filteredOn(l -> l.rawPaidAt().isEmpty())
            .extracting(BatchLine::reference)
            .containsExactly("TES-ABR-000013", "TES-ABR-000048", "TES-ABR-000084");
    }

    @Test
    void marksLinesWithWrongColumnCountInsteadOfFailingTheFile() throws IOException {
        List<BatchLine> lines = reader.read(stream("referencia;numero_factura;valor;fecha_pago\nP-1;FV-1;100\nP-2;FV-2;5;2026-03-15T10:00:00-05:00\n"));

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).formatError()).contains("Se esperaban 4 columnas");
        assertThat(lines.get(1).formatError()).isNull();
    }

    @Test
    void rejectsTheFileOnlyWhenTheHeaderIsWrong() {
        assertThatThrownBy(() -> reader.read(stream("ref;factura;valor\n")))
            .isInstanceOf(CsvBatchReader.InvalidCsvException.class);
        assertThatThrownBy(() -> reader.read(stream("")))
            .isInstanceOf(CsvBatchReader.InvalidCsvException.class);
    }

    private List<BatchLine> read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return reader.read(in);
        }
    }

    private static InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
