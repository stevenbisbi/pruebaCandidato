package com.conciliacion.pagos.application.query;

import java.util.Map;

public record BatchSummary(BatchHeader header, BatchTotals totals, Map<String, Long> rejectionsByReason) {
}
