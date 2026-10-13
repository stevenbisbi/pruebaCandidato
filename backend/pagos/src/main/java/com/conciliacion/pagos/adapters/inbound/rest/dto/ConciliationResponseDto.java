package com.conciliacion.pagos.adapters.inbound.rest.dto;

import com.conciliacion.pagos.domain.model.ConciliationDashboard;
import com.conciliacion.pagos.domain.model.SupplierBalance;

import java.time.LocalDate;
import java.util.List;

/** Tablero de conciliacion (RF-24). Los montos van como texto para no perder precision (RF-05). */
public record ConciliationResponseDto(LocalDate dueFrom, LocalDate dueTo,
                                      long invoiceCount, String totalInvoiced, String totalPending,
                                      long appliedCount, String totalApplied,
                                      long rejectedCount, String totalRejected,
                                      List<SupplierDto> topSuppliers) {

    public record SupplierDto(String nit, String name, String pendingBalance, long invoiceCount) {

        static SupplierDto from(SupplierBalance s) {
            return new SupplierDto(s.nit(), s.name(), s.pendingBalance().toPlainString(), s.invoiceCount());
        }
    }

    public static ConciliationResponseDto from(ConciliationDashboard d) {
        return new ConciliationResponseDto(d.dueFrom(), d.dueTo(),
            d.invoiceCount(), d.totalInvoiced().toPlainString(), d.totalPending().toPlainString(),
            d.appliedCount(), d.totalApplied().toPlainString(),
            d.rejectedCount(), d.totalRejected().toPlainString(),
            d.topSuppliers().stream().map(SupplierDto::from).toList());
    }
}
