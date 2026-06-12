package io.github.shibuna.tripsmith.eval;

import java.util.List;

public record EvaluationCaseResult(
        String id,
        String title,
        String status,
        double dateCoverageRate,
        int verifierErrorCount,
        int verifierWarningCount,
        int budgetViolationCount,
        int invalidLinkCount,
        boolean citationRequired,
        boolean citationSatisfied,
        double toolCallSuccessRate,
        long latencyMs,
        double estimatedTokenCostUsd,
        List<String> issues,
        // Null when the run used the deterministic (no-judge) tier.
        JudgeScores judgeScores
) {
}
