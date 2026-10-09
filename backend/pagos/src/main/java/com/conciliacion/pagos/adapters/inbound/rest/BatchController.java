package com.conciliacion.pagos.adapters.inbound.rest;

import com.conciliacion.pagos.adapters.inbound.csv.CsvBatchReader;
import com.conciliacion.pagos.adapters.inbound.rest.dto.ApiResponses.BatchResponse;
import com.conciliacion.pagos.adapters.inbound.rest.dto.ApiResponses.LinePageResponse;
import com.conciliacion.pagos.application.dto.BatchReceipt;
import com.conciliacion.pagos.application.dto.IncomingBatch;
import com.conciliacion.pagos.application.usecase.GetBatchResultUseCase;
import com.conciliacion.pagos.application.usecase.ProcessBatchUseCase;
import com.conciliacion.pagos.domain.model.BatchLine;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/lotes")
public class BatchController {

    private final ProcessBatchUseCase processBatch;
    private final GetBatchResultUseCase getBatchResult;
    private final CsvBatchReader csvReader;

    public BatchController(ProcessBatchUseCase processBatch, GetBatchResultUseCase getBatchResult,
                           CsvBatchReader csvReader) {
        this.processBatch = processBatch;
        this.getBatchResult = getBatchResult;
        this.csvReader = csvReader;
    }

    /**
     * POST /api/v1/lotes/archivo con el CSV en el campo "archivo" (RF-17).
     * Responde 201 si el lote se proceso ahora, o 200 si ya se habia procesado (reenvio).
     */
    @PostMapping("/archivo")
    public ResponseEntity<BatchResponse> uploadCsv(
        @RequestParam("archivo") MultipartFile file,
        @RequestParam(name = "loteId", required = false) String batchId,
        @RequestParam(name = "origen", defaultValue = "TESORERIA") String source) throws IOException {

        List<BatchLine> lines = csvReader.read(file.getInputStream());
        BatchReceipt receipt = processBatch.process(new IncomingBatch(batchId, source, lines));

        HttpStatus status = HttpStatus.CREATED;
        if (receipt.replayed()) {
            status = HttpStatus.OK;
        }
        return ResponseEntity.status(status).body(BatchResponse.of(receipt.batch(), receipt.replayed()));
    }

    /** GET /api/v1/lotes/{id}: resumen del lote. */
    @GetMapping("/{id}")
    public BatchResponse getBatch(@PathVariable String id) {
        return BatchResponse.of(getBatchResult.getBatch(id), false);
    }

    /** GET /api/v1/lotes/{id}/lineas: resultado linea por linea, paginado (RF-21, RF-22). */
    @GetMapping("/{id}/lineas")
    public LinePageResponse getLines(
        @PathVariable String id,
        @RequestParam(name = "pagina", defaultValue = "0") int page,
        @RequestParam(name = "tamano", defaultValue = "100") int size) {
        return LinePageResponse.of(getBatchResult.getLines(id, page, size));
    }
}
