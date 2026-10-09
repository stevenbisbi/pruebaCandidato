package com.conciliacion.pagos.infrastructure.config;

import com.conciliacion.pagos.application.port.out.BatchRepository;
import com.conciliacion.pagos.application.port.out.DashboardQueryPort;
import com.conciliacion.pagos.application.port.out.InvoiceQueryPort;
import com.conciliacion.pagos.application.port.out.InvoiceRepository;
import com.conciliacion.pagos.application.port.out.PaymentLedger;
import com.conciliacion.pagos.application.port.out.TransactionRunner;
import com.conciliacion.pagos.application.service.BatchQueryService;
import com.conciliacion.pagos.application.service.DashboardService;
import com.conciliacion.pagos.application.service.InvoiceQueryService;
import com.conciliacion.pagos.application.service.ProcessBatchService;
import com.conciliacion.pagos.infrastructure.csv.CsvBatchReader;
import com.conciliacion.pagos.infrastructure.persistence.JdbcBatchRepository;
import com.conciliacion.pagos.infrastructure.persistence.JdbcDashboardQuery;
import com.conciliacion.pagos.infrastructure.persistence.JdbcInvoiceQuery;
import com.conciliacion.pagos.infrastructure.persistence.JdbcInvoiceRepository;
import com.conciliacion.pagos.infrastructure.persistence.JdbcPaymentLedger;
import com.conciliacion.pagos.infrastructure.persistence.SpringTransactionRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Cableado manual de puertos y adaptadores. Las clases de dominio y aplicacion no llevan
 * anotaciones de Spring: este es el unico lugar donde se conocen ambos mundos.
 */
@Configuration
public class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    TransactionRunner transactionRunner(PlatformTransactionManager tm) {
        return new SpringTransactionRunner(new TransactionTemplate(tm));
    }

    @Bean
    InvoiceRepository invoiceRepository(JdbcTemplate jdbc) {
        return new JdbcInvoiceRepository(jdbc);
    }

    @Bean
    BatchRepository batchRepository(JdbcTemplate jdbc) {
        return new JdbcBatchRepository(jdbc);
    }

    @Bean
    PaymentLedger paymentLedger(JdbcTemplate jdbc, PlatformTransactionManager tm) {
        TransactionTemplate readOnly = new TransactionTemplate(tm);
        readOnly.setReadOnly(true);
        readOnly.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return new JdbcPaymentLedger(jdbc, readOnly);
    }

    @Bean
    InvoiceQueryPort invoiceQueryPort(JdbcTemplate jdbc) {
        return new JdbcInvoiceQuery(jdbc);
    }

    @Bean
    DashboardQueryPort dashboardQueryPort(JdbcTemplate jdbc) {
        return new JdbcDashboardQuery(jdbc);
    }

    @Bean
    ProcessBatchService processBatchService(BatchRepository batches, InvoiceRepository invoices,
                                            PaymentLedger ledger, TransactionRunner tx, Clock clock,
                                            @Value("${app.user}") String user) {
        return new ProcessBatchService(batches, invoices, ledger, tx, clock, user);
    }

    @Bean
    BatchQueryService batchQueryService(BatchRepository batches, PaymentLedger ledger) {
        return new BatchQueryService(batches, ledger);
    }

    @Bean
    InvoiceQueryService invoiceQueryService(InvoiceQueryPort query, PaymentLedger ledger) {
        return new InvoiceQueryService(query, ledger);
    }

    @Bean
    DashboardService dashboardService(DashboardQueryPort query) {
        return new DashboardService(query);
    }

    @Bean
    CsvBatchReader csvBatchReader() {
        return new CsvBatchReader();
    }
}
