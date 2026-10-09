package com.conciliacion.pagos.application.query;

import java.math.BigDecimal;
import java.util.List;

public record Dashboard(BigDecimal totalInvoiced, long invoiceCount, BigDecimal totalPending,
                        BigDecimal totalApplied, long appliedCount, BigDecimal totalRejected, long rejectedCount,
                        List<SupplierBalance> topSuppliers) {

    public record SupplierBalance(String nit, String name, BigDecimal pendingBalance) {
    }
}
