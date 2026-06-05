package com.embabel.tripper.eval;

public record EvaluationMetrics(
        int caseCount,
        double dateCoverageRate,
        double budgetViolationRate,
        double invalidLinkRate,
        double citationCoverageRate,
        double toolCallSuccessRate,
        double averageLatencyMs,
        double averageTokenCostUsd,
        int verifierErrorCount,
        int verifierWarningCount
) {
}
