package com.conciliacion.pagos.application.usecase;

/** El recurso pedido no existe. Se responde 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
