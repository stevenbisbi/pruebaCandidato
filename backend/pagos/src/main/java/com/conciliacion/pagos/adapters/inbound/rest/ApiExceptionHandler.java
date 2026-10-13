package com.conciliacion.pagos.adapters.inbound.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.conciliacion.pagos.adapters.inbound.rest.dto.ErrorResponse;
import com.conciliacion.pagos.application.usecase.BatchConflictException;
import com.conciliacion.pagos.adapters.inbound.csv.InvalidCsvException;

/** Traduce las excepciones a respuestas HTTP con un cuerpo uniforme. */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** El archivo no se puede tratar como lote: vacio, encabezado distinto o demasiadas lineas. */
    @ExceptionHandler(InvalidCsvException.class)
    public ResponseEntity<ErrorResponse> invalidCsv(InvalidCsvException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** El id del lote ya existe con otro contenido (A-07). */
    @ExceptionHandler(BatchConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(BatchConflictException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
            .body(new ErrorResponse(status.value(), status.getReasonPhrase(), message));
    }

        /** La peticion no trae el archivo, o no viene como multipart/form-data. */
    @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ErrorResponse> missingFile(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "Falta el archivo CSV en el campo 'file' (multipart/form-data)");
    }
}