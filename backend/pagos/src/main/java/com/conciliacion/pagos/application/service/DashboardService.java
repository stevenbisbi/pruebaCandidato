package com.conciliacion.pagos.application.service;

import com.conciliacion.pagos.application.port.out.DashboardQueryPort;
import com.conciliacion.pagos.application.query.Dashboard;

import java.time.LocalDate;

/** Tablero de conciliacion (RF-24). */
public class DashboardService {

    private final DashboardQueryPort query;

    public DashboardService(DashboardQueryPort query) {
        this.query = query;
    }

    public Dashboard load(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("La fecha inicial es posterior a la final");
        }
        return query.load(from, to);
    }
}
