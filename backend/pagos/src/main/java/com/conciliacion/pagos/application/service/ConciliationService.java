package com.conciliacion.pagos.application.service;

import com.conciliacion.pagos.application.usecase.GetConciliationStateUseCase;
import com.conciliacion.pagos.application.usecase.InvalidRequestException;
import com.conciliacion.pagos.domain.model.ConciliationDashboard;
import com.conciliacion.pagos.domain.repository.DashboardQueryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** Tablero de conciliacion (RF-24). */
@Service
public class ConciliationService implements GetConciliationStateUseCase {

    private final DashboardQueryPort dashboard;

    public ConciliationService(DashboardQueryPort dashboard) {
        this.dashboard = dashboard;
    }

    /**
     * Las consultas del tablero se leen en una sola transaccion REPEATABLE READ: todas ven la misma
     * foto de la base, asi los totales cuadran entre si aunque un lote se este aplicando.
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ConciliationDashboard getDashboard(LocalDate dueFrom, LocalDate dueTo) {
        if (dueFrom != null && dueTo != null && dueFrom.isAfter(dueTo)) {
            throw new InvalidRequestException("El vencimiento desde no puede ser posterior al vencimiento hasta");
        }
        return dashboard.summarize(dueFrom, dueTo);
    }
}
