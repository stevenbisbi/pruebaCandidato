package com.conciliacion.pagos.domain.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Regla de vencimiento (RF-13). La zona de operacion es fija y explicita: el contenedor corre
 * en UTC y no debe influir. Usar la zona por defecto de la JVM aqui seria exactamente el error
 * que hace pasar CF-05 en un portatil en Colombia y fallar en produccion.
 */
public final class DueDatePolicy {

    public static final ZoneId OPERATION_ZONE = ZoneId.of("America/Bogota");

    private DueDatePolicy() {
    }

    /**
     * Una factura vence a las 23:59:59 del dia de vencimiento en hora de Colombia. Se compara
     * contra el inicio del dia siguiente (exclusivo) para no perder las fracciones de segundo
     * entre 23:59:59 y 00:00:00.
     */
    public static boolean isLate(LocalDate dueDate, Instant paidAt) {
        Instant firstLateInstant = dueDate.plusDays(1).atStartOfDay(OPERATION_ZONE).toInstant();
        return !paidAt.isBefore(firstLateInstant);
    }
}
