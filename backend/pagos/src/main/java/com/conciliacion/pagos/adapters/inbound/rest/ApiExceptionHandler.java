package com.conciliacion.pagos.adapters.inbound.rest;

import com.conciliacion.pagos.adapters.inbound.csv.InvalidCsvException;
import com.conciliacion.pagos.adapters.inbound.rest.dto.ErrorResponse;
import com.conciliacion.pagos.application.usecase.BatchConflictException;
import com.conciliacion.pagos.application.usecase.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Traduce las excepciones a respuestas HTTP con un cuerpo uniforme (ErrorResponse). */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** El archivo no se puede tratar como lote: vacio, encabezado distinto o demasiadas lineas. */
    @ExceptionHandler(InvalidCsvException.class)
    public ResponseEntity<ErrorResponse> invalidCsv(InvalidCsvException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** La peticion no trae el archivo, o no viene como multipart/form-data. */
    @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ErrorResponse> missingFile(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "Falta el archivo CSV en el campo 'file' (multipart/form-data)");
    }

    /** El JSON no se puede leer: mal escrito, o un campo del lote con formato invalido (fechaGeneracion). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadableBody(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST,
            "El cuerpo de la peticion no es un JSON valido o tiene un campo con formato incorrecto");
    }

    /** Falla una validacion del cuerpo, por ejemplo un lote sin pagos (@NotEmpty). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException e) {
        FieldError field = e.getBindingResult().getFieldError();
        String message = field == null ? "La peticion no es valida" : field.getDefaultMessage();
        return error(HttpStatus.BAD_REQUEST, message);
    }

    /** Un parametro de la URL no tiene el tipo esperado, por ejemplo page=abc o outcome=OTRO. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> invalidParameter(MethodArgumentTypeMismatchException e) {
        return error(HttpStatus.BAD_REQUEST, "Valor invalido para el parametro '" + e.getName() + "': " + e.getValue());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
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
}
