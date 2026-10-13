package com.conciliacion.pagos.adapters.inbound.rest;

import com.conciliacion.pagos.adapters.inbound.csv.CsvBatchReader;
import com.conciliacion.pagos.adapters.inbound.rest.dto.BatchPayRequestDto;
import com.conciliacion.pagos.adapters.inbound.rest.dto.BatchPayResponseDto;
import com.conciliacion.pagos.adapters.inbound.rest.dto.LineResultResponseDto;
import com.conciliacion.pagos.adapters.inbound.rest.dto.PageResponseDto;
import com.conciliacion.pagos.application.dto.BatchReceipt;
import com.conciliacion.pagos.application.dto.IncomingBatch;
import com.conciliacion.pagos.application.usecase.GetBatchResultUseCase;
import com.conciliacion.pagos.application.usecase.ReconcileBatchUseCase;
import com.conciliacion.pagos.domain.model.BatchChannel;
import com.conciliacion.pagos.domain.model.BatchLine;
import com.conciliacion.pagos.domain.model.LineOutcome;
import com.conciliacion.pagos.domain.model.LinePage;
import com.conciliacion.pagos.domain.model.RejectionReason;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/** Lotes de pago: recepcion por JSON o CSV (RF-17) y consulta del resultado (RF-21, RF-22). */
@RestController
@RequestMapping("/api/v1/batches")
public class BatchController {

    private final ReconcileBatchUseCase reconcileBatch;
    private final GetBatchResultUseCase batchResult;
    private final CsvBatchReader csvReader;

    public BatchController(ReconcileBatchUseCase reconcileBatch, GetBatchResultUseCase batchResult,
                           CsvBatchReader csvReader) {
        this.reconcileBatch = reconcileBatch;
        this.batchResult = batchResult;
        this.csvReader = csvReader;
    }

    /** POST /api/v1/batches: lote en JSON (formato del Anexo A). */
    @PostMapping
    public ResponseEntity<BatchPayResponseDto> receiveJson(@Valid @RequestBody BatchPayRequestDto request) {
        return respond(reconcileBatch.reconcile(request.toIncomingBatch()));
    }

    /** POST /api/v1/batches/file: lote en CSV, en el campo "file" (multipart/form-data). */
    @PostMapping("/file")
    public ResponseEntity<BatchPayResponseDto> receiveCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "id", required = false) String id,
            @RequestParam(name = "source", defaultValue = "TESORERIA") String source) throws IOException {
        List<BatchLine> lines = csvReader.read(file.getInputStream());
        return respond(reconcileBatch.reconcile(new IncomingBatch(id, source, BatchChannel.CSV, null, lines)));
    }

    /** GET /api/v1/batches/{id}: resumen de un lote procesado (RF-22). */
    @GetMapping("/{id}")
    public BatchPayResponseDto getBatch(@PathVariable String id) {
        return BatchPayResponseDto.from(batchResult.getBatch(id), false);
    }

    /**
     * GET /api/v1/batches/{id}/lines: resultado linea por linea, paginado (RF-21, RF-22), con
     * filtro opcional por resultado y por motivo de rechazo (RF-26).
     */
    @GetMapping("/{id}/lines")
    public PageResponseDto<LineResultResponseDto> getLines(
            @PathVariable String id,
            @RequestParam(name = "outcome", required = false) LineOutcome outcome,
            @RequestParam(name = "reason", required = false) RejectionReason reason,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "100") int size) {
        LinePage result = batchResult.getLines(id, outcome, reason, page, size);
        return new PageResponseDto<>(
            result.items().stream().map(LineResultResponseDto::from).toList(),
            result.page(), result.size(), result.totalItems(), result.hasNext());
    }

    /** GET /api/v1/batches/{id}/download: resultado completo en CSV (RF-22, A-09). Pendiente. */
    @GetMapping("/{id}/download")
    public void downloadResult(@PathVariable String id) {
        // Se implementa en su propio commit.
    }

    /** 201 si el lote se proceso ahora, 200 si era un reenvio (RF-20). */
    private ResponseEntity<BatchPayResponseDto> respond(BatchReceipt receipt) {
        HttpStatus status = receipt.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(BatchPayResponseDto.from(receipt.batch(), receipt.replayed()));
    }
}
