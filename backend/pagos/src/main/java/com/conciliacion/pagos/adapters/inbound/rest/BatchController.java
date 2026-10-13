package com.conciliacion.pagos.adapters.inbound.rest;

import java.util.List;

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


import com.conciliacion.pagos.adapters.inbound.csv.CsvBatchReader;
import com.conciliacion.pagos.adapters.inbound.rest.dto.BatchPayRequestDto;
import com.conciliacion.pagos.adapters.inbound.rest.dto.BatchPayResponseDto;
import com.conciliacion.pagos.application.dto.BatchReceipt;
import com.conciliacion.pagos.application.dto.IncomingBatch;
import com.conciliacion.pagos.application.usecase.ReconcileBatchUseCase;
import com.conciliacion.pagos.domain.model.BatchChannel;
import com.conciliacion.pagos.domain.model.BatchLine;

import jakarta.validation.Valid;


@RestController
@RequestMapping("/api/v1/batches")
public class BatchController {

    private final ReconcileBatchUseCase reconcileBatch;
    private final CsvBatchReader csvReader;

    public BatchController(ReconcileBatchUseCase reconcileBatch, CsvBatchReader csvReader) {
        this.reconcileBatch = reconcileBatch;
        this.csvReader = csvReader;
    }
    
    @PostMapping ("")
    public ResponseEntity<BatchPayResponseDto> receiveJson(@Valid @RequestBody BatchPayRequestDto request) {
        return respond(reconcileBatch.reconcile(request.toIncomingBatch()));
    }
    @PostMapping ("/file")
    public ResponseEntity<BatchPayResponseDto> receiveCsv(@RequestParam ("file") MultipartFile file,
                        @RequestParam (name = "id", required = false) String id,
                         @RequestParam(name = "source", defaultValue = "TESORERIA") String source) throws 
    IOException {
        List<BatchLine> lines = csvReader.read(file.getInputStream());
        BatchReceipt receipt = reconcileBatch.reconcile(
            new IncomingBatch(id, source, BatchChannel.CSV, null, lines));

        HttpStatus status = receipt.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(BatchPayResponseDto.from(receipt.batch(), receipt.replayed()));
    }

    @GetMapping ("/{id}")
    public void getBatch(@PathVariable String id) {
    }

     @GetMapping ("/{id}/lines")
    public void getLines(@PathVariable String id,
                                  @RequestParam (name = "page", defaultValue = "0") Integer page,
                                  @RequestParam (name = "size", defaultValue = "100") Integer size) {
    }

     @GetMapping ("/{id}/download")
    public void downloadResult(@PathVariable String id) {
    }
        /** 201 si el lote se proceso ahora, 200 si era un reenvio (RF-20). */
        private ResponseEntity<BatchPayResponseDto> respond(BatchReceipt receipt) {
        HttpStatus status = receipt.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(BatchPayResponseDto.from(receipt.batch(), receipt.replayed()));
    }
}
