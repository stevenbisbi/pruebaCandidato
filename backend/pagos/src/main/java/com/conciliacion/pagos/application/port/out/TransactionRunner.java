package com.conciliacion.pagos.application.port.out;

import java.util.function.Supplier;

/** Abstraccion de la unidad de trabajo, para que la aplicacion no dependa de Spring. */
public interface TransactionRunner {

    <T> T inTransaction(Supplier<T> work);
}
