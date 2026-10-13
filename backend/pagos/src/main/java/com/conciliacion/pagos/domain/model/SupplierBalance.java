package com.conciliacion.pagos.domain.model;

import java.math.BigDecimal;

/** Un proveedor con su saldo pendiente total, para el top 10 del tablero (RF-24). */
public record SupplierBalance(String nit, String name, BigDecimal pendingBalance, long invoiceCount) {
}
