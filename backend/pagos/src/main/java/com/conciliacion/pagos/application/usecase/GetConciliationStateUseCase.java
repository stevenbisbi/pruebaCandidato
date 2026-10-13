package com.conciliacion.pagos.application.usecase;

import com.conciliacion.pagos.domain.model.ConciliationDashboard;

import java.time.LocalDate;

/** Puerto de entrada: tablero de conciliacion sobre un rango de vencimiento (RF-24, A-08). */
public interface GetConciliationStateUseCase {

    /**
     * @param dueFrom vencimiento desde (inclusive); null sin limite
     * @param dueTo vencimiento hasta (inclusive); null sin limite
     * @throws InvalidRequestException si el rango esta invertido
     */
    ConciliationDashboard getDashboard(LocalDate dueFrom, LocalDate dueTo);
}
