package com.creditintelligence.analysis.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class IncomeConsistencyRule {
    public static final String CODE = "INCOME_CONSISTENCY";
    public static final String VERSION = "1.0";

    public Result evaluate(BigDecimal declared, BigDecimal documentIncome, BigDecimal tolerancePercent,
            String evidenceRef) {
        if (documentIncome == null) {
            return new Result("NOT_EVALUATED",
                    "Monthly income evidence is missing or ambiguous", null);
        }
        if (documentIncome.signum() < 0) {
            throw new IllegalArgumentException("Negative extracted income");
        }
        var difference = declared.subtract(documentIncome).abs();
        var percentage = documentIncome.signum() == 0
                ? (difference.signum() == 0 ? BigDecimal.ZERO : new BigDecimal("1000000"))
                : difference.multiply(new BigDecimal("100"))
                        .divide(documentIncome, 4, RoundingMode.HALF_UP);
        var result = percentage.compareTo(tolerancePercent) > 0 ? "WARNING" : "PASS";
        var detail = "Declared=" + declared.setScale(2, RoundingMode.HALF_UP)
                + " BRL; document=" + documentIncome.setScale(2, RoundingMode.HALF_UP)
                + " BRL; difference=" + percentage + "%; tolerance=" + tolerancePercent + "%";
        return new Result(result, detail, evidenceRef);
    }

    public record Result(String result, String details, String evidenceRef) {}
}
