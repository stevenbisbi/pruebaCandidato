package com.conciliacion.pagos.infrastructure.web;

import com.conciliacion.pagos.application.service.BatchConflictException;
import com.conciliacion.pagos.application.service.NotFoundException;
import com.conciliacion.pagos.infrastructure.csv.CsvBatchReader;
import com.conciliacion.pagos.infrastructure.web.dto.ApiResponses.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", e.getMessage());
    }

    @ExceptionHandler(BatchConflictException.class)
    ResponseEntity<ErrorResponse> conflict(BatchConflictException e) {
        return error(HttpStatus.CONFLICT, "LOTE_CONFLICTIVO", e.getMessage());
    }

    /**
     * Dos lotes distintos procesados a la vez con la misma referencia de pago: el indice unico
     * rechaza el segundo completo. Se responde 409 para que Tesoreria reintente; en el reintento
     * la referencia aparecera como duplicada y se rechazara solo esa linea.
     */
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ErrorResponse> duplicateKey(DuplicateKeyException e) {
        LOG.warn("Conflicto de concurrencia al registrar un lote", e);
        return error(HttpStatus.CONFLICT, "CONFLICTO_CONCURRENCIA",
            "Otro lote aplico al mismo tiempo una referencia de este lote. Reintente el envio.");
    }

    @ExceptionHandler({CsvBatchReader.InvalidCsvException.class, IllegalArgumentException.class,
        MethodArgumentTypeMismatchException.class, MissingServletRequestPartException.class})
    ResponseEntity<ErrorResponse> badRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage()).reduce((a, b) -> a + "; " + b)
            .orElse("Solicitud invalida");
        return error(HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "JSON_INVALIDO", "El cuerpo no es un JSON valido para un lote");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> tooLarge(MaxUploadSizeExceededException e) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "ARCHIVO_DEMASIADO_GRANDE", "El archivo supera el tamano permitido");
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
