package io.github.shibuna.tripsmith.eval;

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
        int verifierWarningCount,
        // Mean of the judge's five-dimension average across judged cases; null when no judge ran.
        Double averageJudgeOverall
) {
}
