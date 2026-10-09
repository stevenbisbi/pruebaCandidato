package com.conciliacion.pagos.infrastructure.web;

import com.conciliacion.pagos.application.query.BatchReceipt;
import com.conciliacion.pagos.application.query.IncomingBatch;
import com.conciliacion.pagos.application.service.BatchQueryService;
import com.conciliacion.pagos.application.service.ProcessBatchService;
import com.conciliacion.pagos.domain.model.BatchLine;
import com.conciliacion.pagos.domain.model.LineOutcome;
import com.conciliacion.pagos.domain.model.RejectionReason;
import com.conciliacion.pagos.infrastructure.csv.CsvBatchReader;
import com.conciliacion.pagos.infrastructure.web.dto.ApiResponses.BatchResponse;
import com.conciliacion.pagos.infrastructure.web.dto.ApiResponses.LinePageResponse;
import com.conciliacion.pagos.infrastructure.web.dto.BatchRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/lotes")
public class BatchController {

    private final ProcessBatchService processBatch;
    private final BatchQueryService batchQuery;
    private final CsvBatchReader csvReader;

    public BatchController(ProcessBatchService processBatch, BatchQueryService batchQuery, CsvBatchReader csvReader) {
        this.processBatch = processBatch;
        this.batchQuery = batchQuery;
        this.csvReader = csvReader;
    }

    /** 201 si el lote se proceso ahora; 200 si es un reenvio y se devuelve el resultado original. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BatchResponse> receiveJson(@Valid @RequestBody BatchRequest request) {
        List<BatchLine> lines = new ArrayList<>(request.payments().size());
        int n = 0;
        for (BatchRequest.PaymentRequest p : request.payments()) {
            n++;
            lines.add(p == null ? BatchLine.malformed(n, "Pago nulo")
                : BatchLine.of(n, p.reference(), p.invoiceNumber(), p.amount(), p.paidAt()));
        }
        return respond(processBatch.process(
            new IncomingBatch(request.batchId(), request.source(), "JSON", request.generatedOn(), lines)));
    }

    /**
     * El CSV no trae identificador de lote. Si no se envia loteId, se deriva de la huella del
     * contenido: subir el mismo archivo dos veces (doble clic, recarga, reenvio) es el mismo lote.
     */
    @PostMapping(path = "/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BatchResponse> receiveCsv(@RequestPart("archivo") MultipartFile file,
                                                    @RequestParam(name = "loteId", required = false) String batchId,
                                                    @RequestParam(name = "origen", defaultValue = "TESORERIA") String source)
        throws IOException {
        List<BatchLine> lines = csvReader.read(file.getInputStream());
        return respond(processBatch.process(new IncomingBatch(batchId, source, "CSV", null, lines)));
    }

    @GetMapping("/{id}")
    public BatchResponse detail(@PathVariable String id) {
        return BatchResponse.of(batchQuery.summary(id), false);
    }

    @GetMapping("/{id}/lineas")
    public LinePageResponse lines(@PathVariable String id,
                                  @RequestParam(name = "resultado", required = false) LineOutcome outcome,
                                  @RequestParam(name = "motivo", required = false) RejectionReason reason,
                                  @RequestParam(name = "pagina", defaultValue = "0") int page,
                                  @RequestParam(name = "tamano", defaultValue = "100") int size) {
        return LinePageResponse.of(batchQuery.lines(id, outcome, reason, page, size));
    }

    /** RF-22: descarga del resultado linea por linea, en el mismo separador que usa Tesoreria. */
    @GetMapping("/{id}/resultado")
    public void download(@PathVariable String id, HttpServletResponse response) throws IOException {
        batchQuery.summary(id);
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"resultado-" + safe(id) + ".csv\"");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        PrintWriter out = response.getWriter();
        out.println("linea;referencia;numero_factura;valor;fecha_pago;resultado;motivo;detalle;saldo_anterior;saldo_posterior;estado_factura");
        batchQuery.export(id, r -> out.println(String.join(";",
            String.valueOf(r.lineNumber()), csv(r.reference()), csv(r.invoiceNumber()),
            r.amount() == null ? "" : r.amount().toPlainString(), r.paidAt() == null ? "" : r.paidAt().toString(),
            r.outcome().name(), r.reason() == null ? "" : r.reason().name(), csv(r.detail()),
            r.balanceBefore() == null ? "" : r.balanceBefore().toPlainString(),
            r.balanceAfter() == null ? "" : r.balanceAfter().toPlainString(),
            r.statusAfter() == null ? "" : r.statusAfter().name())));
        out.flush();
        if (out.checkError()) {
            throw new UncheckedIOException(new IOException("Error escribiendo la descarga"));
        }
    }

    private static ResponseEntity<BatchResponse> respond(BatchReceipt receipt) {
        HttpStatus status = receipt.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(BatchResponse.of(receipt.summary(), receipt.replayed()));
    }

    private static String csv(String value) {
        return Objects.toString(value, "").replace(';', ',').replace('\n', ' ').replace('\r', ' ');
    }

    private static String safe(String id) {
        return id.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
