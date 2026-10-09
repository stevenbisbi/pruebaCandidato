package com.conciliacion.pagos.application.port.out;

import com.conciliacion.pagos.application.query.Dashboard;

import java.time.LocalDate;

public interface DashboardQueryPort {

    Dashboard load(LocalDate from, LocalDate to);
}
