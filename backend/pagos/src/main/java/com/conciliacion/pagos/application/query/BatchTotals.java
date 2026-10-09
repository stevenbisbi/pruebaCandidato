package com.conciliacion.pagos.application.query;

import com.conciliacion.pagos.domain.model.LineResult;

import java.math.BigDecimal;
import java.util.List;

public record BatchTotals(int totalLines, int appliedLines, int rejectedLines, BigDecimal appliedAmount,
                          BigDecimal rejectedAmount) {

    public static BatchTotals of(List<LineResult> results) {
        int applied = 0;
        BigDecimal appliedAmount = BigDecimal.ZERO.setScale(2);
        BigDecimal rejectedAmount = BigDecimal.ZERO.setScale(2);
        for (LineResult r : results) {
            BigDecimal amount = r.amount() == null ? BigDecimal.ZERO : r.amount();
            if (r.isApplied()) {
                applied++;
                appliedAmount = appliedAmount.add(amount);
            } else {
                rejectedAmount = rejectedAmount.add(amount);
            }
        }
        return new BatchTotals(results.size(), applied, results.size() - applied, appliedAmount, rejectedAmount);
    }
}
