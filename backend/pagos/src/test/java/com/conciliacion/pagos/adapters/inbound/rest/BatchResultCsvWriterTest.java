package com.conciliacion.pagos.adapters.inbound.rest;

import com.conciliacion.pagos.domain.model.InvoiceStatus;
import com.conciliacion.pagos.domain.model.LineOutcome;
import com.conciliacion.pagos.domain.model.LineResult;
import com.conciliacion.pagos.domain.model.RejectionReason;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Formato del CSV de descarga del resultado (RF-22, A-09). */
class BatchResultCsvWriterTest {

    private static final OffsetDateTime PAID_AT = OffsetDateTime.parse("2026-03-15T23:30:00-05:00");

    @Test
    void writesTheHeaderAndOneRowPerLine() {
        StringWriter out = new StringWriter();
        BatchResultCsvWriter writer = new BatchResultCsvWriter(out);

        writer.accept(new LineResult(1, "P-1", "FV-1", new BigDecimal("37500483.00"), PAID_AT, LineOutcome.APLICADO,
            null, null, new BigDecimal("37500483.40"), new BigDecimal("0.00"), InvoiceStatus.PAGADA,
            new BigDecimal("0.40")));
        writer.accept(new LineResult(2, "P-2", "FV-404", new BigDecimal("1000.00"), PAID_AT, LineOutcome.RECHAZADO,
            RejectionReason.FACTURA_INEXISTENTE, null, null, null, null, null));

        assertThat(out.toString().split("\r\n")).containsExactly(
            BatchResultCsvWriter.HEADER,
            "1;P-1;FV-1;37500483.00;2026-03-15T23:30-05:00;APLICADO;;;37500483.40;0.00;PAGADA;0.40",
            "2;P-2;FV-404;1000.00;2026-03-15T23:30-05:00;RECHAZADO;FACTURA_INEXISTENTE;;;;;");
    }

    @Test
    void writesALineWithoutAmountOrDate() {
        StringWriter out = new StringWriter();

        new BatchResultCsvWriter(out).accept(new LineResult(3, "P-3", "FV-1", null, null, LineOutcome.RECHAZADO,
            RejectionReason.VALOR_INVALIDO, "Valor recibido: 'abc'", null, null, null, null));

        assertThat(out.toString().split("\r\n")[1])
            .isEqualTo("3;P-3;FV-1;;;RECHAZADO;VALOR_INVALIDO;Valor recibido: 'abc';;;;");
    }

    @Test
    void quotesTextsThatContainTheSeparatorQuotesOrLineBreaks() {
        assertThat(BatchResultCsvWriter.field("simple")).isEqualTo("simple");
        assertThat(BatchResultCsvWriter.field("a;b")).isEqualTo("\"a;b\"");
        assertThat(BatchResultCsvWriter.field("dice \"hola\"")).isEqualTo("\"dice \"\"hola\"\"\"");
        assertThat(BatchResultCsvWriter.field("dos\nlineas")).isEqualTo("\"dos\nlineas\"");
        assertThat(BatchResultCsvWriter.field(null)).isEmpty();
    }
}
