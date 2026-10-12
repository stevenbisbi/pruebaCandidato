package com.conciliacion.pagos.adapters.inbound.csv;

/**
 * El archivo no se puede tratar como un lote: esta vacio, no trae el encabezado acordado o supera
 * el limite de lineas. Es el unico caso en que se rechaza el archivo completo; una linea mala
 * nunca lo provoca.
 */
public class InvalidCsvException extends RuntimeException {

    public InvalidCsvException(String message) {
        super(message);
    }
}
