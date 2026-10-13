package com.conciliacion.pagos.application.usecase;

/** La peticion tiene datos incoherentes, por ejemplo un rango de fechas invertido. Se responde 400. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
