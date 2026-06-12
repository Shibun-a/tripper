package io.github.shibuna.tripsmith.eval;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

public record EvaluationReport(
        Instant generatedAt,
        String datasetPath,
        String runner,
        EvaluationMetrics metrics,
        List<EvaluationCaseResult> cases
) {

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Travel Agent Evaluation Report\n\n");
        sb.append("- Generated at: ").append(generatedAt).append('\n');
        sb.append("- Dataset: `").append(datasetPath).append("`\n");
        sb.append("- Runner: `").append(runner).append("`\n\n");

        sb.append("## Summary\n\n");
        sb.append("| Metric | Value |\n");
        sb.append("| --- | ---: |\n");
        sb.append("| Cases | ").append(metrics.caseCount()).append(" |\n");
        sb.append("| Date coverage rate | ").append(percent(metrics.dateCoverageRate())).append(" |\n");
        sb.append("| Budget violation rate | ").append(percent(metrics.budgetViolationRate())).append(" |\n");
        sb.append("| Invalid link rate | ").append(percent(metrics.invalidLinkRate())).append(" |\n");
        sb.append("| Citation coverage rate | ").append(percent(metrics.citationCoverageRate())).append(" |\n");
        sb.append("| Tool-call success rate | ").append(percent(metrics.toolCallSuccessRate())).append(" |\n");
        sb.append("| Average latency | ").append(String.format(Locale.ROOT, "%.1f ms", metrics.averageLatencyMs())).append(" |\n");
        sb.append("| Average token cost | $").append(String.format(Locale.ROOT, "%.4f", metrics.averageTokenCostUsd())).append(" |\n");
        sb.append("| Verifier errors | ").append(metrics.verifierErrorCount()).append(" |\n");
        sb.append("| Verifier warnings | ").append(metrics.verifierWarningCount()).append(" |\n");
        if (metrics.averageJudgeOverall() != null) {
            sb.append("| Avg judge score (1-5) | ")
                    .append(String.format(Locale.ROOT, "%.2f", metrics.averageJudgeOverall())).append(" |\n");
        }
        sb.append('\n');

        sb.append("## Cases\n\n");
        sb.append("| Case | Status | Date Coverage | Errors | Warnings | Citation | Judge (1-5) |\n");
        sb.append("| --- | --- | ---: | ---: | ---: | --- | ---: |\n");
        for (EvaluationCaseResult result : cases) {
            sb.append("| `").append(result.id()).append("` | ")
                    .append(result.status()).append(" | ")
                    .append(percent(result.dateCoverageRate())).append(" | ")
                    .append(result.verifierErrorCount()).append(" | ")
                    .append(result.verifierWarningCount()).append(" | ")
                    .append(result.citationRequired() ? (result.citationSatisfied() ? "yes" : "no") : "n/a")
                    .append(" | ")
                    .append(result.judgeScores() == null
                            ? "n/a"
                            : String.format(Locale.ROOT, "%.1f", result.judgeScores().averageScore()))
                    .append(" |\n");
        }
        return sb.toString();
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100.0);
    }
}
