package com.conciliacion.pagos.infrastructure.persistence;

import com.conciliacion.pagos.application.port.out.TransactionRunner;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

public class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate template;

    public SpringTransactionRunner(TransactionTemplate template) {
        this.template = template;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return template.execute(status -> work.get());
    }
}
