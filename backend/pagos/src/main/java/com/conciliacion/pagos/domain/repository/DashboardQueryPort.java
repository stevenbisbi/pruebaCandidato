package com.conciliacion.pagos.domain.repository;

import com.conciliacion.pagos.domain.model.ConciliationDashboard;

import java.time.LocalDate;

/** Puerto de salida: totales y top de proveedores del tablero de conciliacion (RF-24). */
public interface DashboardQueryPort {

    /**
     * @param dueFrom vencimiento desde (inclusive); null sin limite
     * @param dueTo vencimiento hasta (inclusive); null sin limite
     */
    ConciliationDashboard summarize(LocalDate dueFrom, LocalDate dueTo);
}
