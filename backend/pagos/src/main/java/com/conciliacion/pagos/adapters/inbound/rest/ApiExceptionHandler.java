package com.conciliacion.pagos.adapters.inbound.rest;

import com.conciliacion.pagos.adapters.inbound.csv.CsvBatchReader;
import com.conciliacion.pagos.adapters.inbound.rest.dto.ApiResponses.ErrorResponse;
import com.conciliacion.pagos.application.usecase.BatchConflictException;
import com.conciliacion.pagos.application.usecase.NotFoundException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Convierte las excepciones en respuestas HTTP con un JSON { codigo, mensaje }. */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** 404: el lote pedido no existe. */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", e.getMessage());
    }

    /** 409: llego un lote con un id ya usado pero con otro contenido. */
    @ExceptionHandler(BatchConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(BatchConflictException e) {
        return error(HttpStatus.CONFLICT, "LOTE_CONFLICTIVO", e.getMessage());
    }

    /**
     * 409: dos lotes distintos aplicaron a la vez la misma referencia nueva. El indice unico de la
     * base rechaza el segundo completo; al reenviarlo, esa linea saldra como REFERENCIA_DUPLICADA.
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> duplicateKey(DuplicateKeyException e) {
        return error(HttpStatus.CONFLICT, "CONFLICTO_CONCURRENCIA",
            "Otro lote aplico al mismo tiempo una referencia de este lote. Reintente el envio.");
    }

    /** 400: archivo vacio, encabezado invalido, parametro con formato incorrecto o sin archivo. */
    @ExceptionHandler({CsvBatchReader.InvalidCsvException.class, IllegalArgumentException.class,
        MethodArgumentTypeMismatchException.class, MissingServletRequestPartException.class,
        MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> badRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA", e.getMessage());
    }

    /** 413: el archivo pesa mas de 20 MB. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> tooLarge(MaxUploadSizeExceededException e) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "ARCHIVO_DEMASIADO_GRANDE", "El archivo supera el tamano permitido");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
