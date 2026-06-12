package io.github.shibuna.tripsmith.eval;

import io.github.shibuna.tripsmith.verification.ItineraryDay;
import io.github.shibuna.tripsmith.verification.ItineraryVerificationRequest;
import io.github.shibuna.tripsmith.verification.ItineraryVerificationService;
import io.github.shibuna.tripsmith.verification.PlanIssueCategory;
import io.github.shibuna.tripsmith.verification.PlanVerificationIssue;
import io.github.shibuna.tripsmith.verification.PlanVerificationResult;
import io.github.shibuna.tripsmith.verification.VerificationSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

public class TravelEvaluationHarness {

    private static final Logger logger = LoggerFactory.getLogger(TravelEvaluationHarness.class);

    private final EvalPlanCandidateFactory candidateFactory;
    private final ItineraryVerificationService verificationService;
    // Null in the deterministic CI tier; set in the agent tier to add subjective LLM-judge scores.
    private final PlanJudge judge;

    public TravelEvaluationHarness(
            EvalPlanCandidateFactory candidateFactory,
            ItineraryVerificationService verificationService
    ) {
        this(candidateFactory, verificationService, null);
    }

    public TravelEvaluationHarness(
            EvalPlanCandidateFactory candidateFactory,
            ItineraryVerificationService verificationService,
            PlanJudge judge
    ) {
        this.candidateFactory = candidateFactory;
        this.verificationService = verificationService;
        this.judge = judge;
    }

    public EvaluationReport run(
            List<TravelEvalCase> cases,
            String datasetPath,
            int limit
    ) {
        List<TravelEvalCase> selectedCases = limit > 0 && limit < cases.size()
                ? cases.subList(0, limit)
                : cases;

        List<EvaluationCaseResult> caseResults = selectedCases.stream()
                .map(this::evaluateCase)
                .toList();

        return new EvaluationReport(
                Instant.now(),
                datasetPath,
                candidateFactory.getClass().getSimpleName(),
                aggregate(caseResults),
                caseResults
        );
    }

    private EvaluationCaseResult evaluateCase(TravelEvalCase evalCase) {
        EvalPlanCandidate candidate = candidateFactory.create(evalCase);
        PlanVerificationResult verification = verificationService.verifyTravelPlan(
                candidate.verificationRequest(),
                false,
                0
        );

        // INFO-level budget notes (likely multi-day figures) are not violations.
        int budgetViolations = (int) verification.getIssues().stream()
                .filter(issue -> issue.getCategory() == PlanIssueCategory.BUDGET_EXCEEDED)
                .filter(issue -> issue.getSeverity() != VerificationSeverity.INFO)
                .count();
        int invalidLinks = countIssues(verification, PlanIssueCategory.INVALID_LINK);
        boolean citationSatisfied = !evalCase.requiresKnowledgeCitation()
                || candidate.verificationRequest().planText().contains("[KB:");
        double dateCoverageRate = dateCoverageRate(candidate.verificationRequest());
        double toolCallSuccessRate = candidate.toolCallAttempts() == 0
                ? 1.0
                : (double) candidate.successfulToolCalls() / candidate.toolCallAttempts();
        JudgeScores judgeScores = judgeIfEnabled(evalCase, candidate);

        return new EvaluationCaseResult(
                evalCase.id(),
                evalCase.title(),
                verification.getStatus(),
                dateCoverageRate,
                (int) verification.getErrorCount(),
                (int) verification.getWarningCount(),
                budgetViolations,
                invalidLinks,
                evalCase.requiresKnowledgeCitation(),
                citationSatisfied,
                toolCallSuccessRate,
                candidate.latencyMs(),
                candidate.estimatedTokenCostUsd(),
                verification.getIssues().stream().map(PlanVerificationIssue::getPromptLine).toList(),
                judgeScores
        );
    }

    private JudgeScores judgeIfEnabled(TravelEvalCase evalCase, EvalPlanCandidate candidate) {
        if (judge == null) {
            return null;
        }
        try {
            return judge.judge(
                    candidate.verificationRequest().planText(),
                    evalCase.interests(),
                    evalCase.constraints(),
                    evalCase.expectedThemes(),
                    evalCase.expectedCountries()
            );
        } catch (RuntimeException ex) {
            // One judge failure should not abort the batch; record no score for this case.
            logger.warn("Judge failed for eval case {}: {}", evalCase.id(), ex.getMessage());
            return null;
        }
    }

    private EvaluationMetrics aggregate(List<EvaluationCaseResult> results) {
        int caseCount = results.size();
        if (caseCount == 0) {
            return new EvaluationMetrics(0, 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 0, 0, null);
        }

        int citationRequired = (int) results.stream().filter(EvaluationCaseResult::citationRequired).count();
        int citationSatisfied = (int) results.stream()
                .filter(EvaluationCaseResult::citationRequired)
                .filter(EvaluationCaseResult::citationSatisfied)
                .count();
        int verifierErrors = results.stream().mapToInt(EvaluationCaseResult::verifierErrorCount).sum();
        int verifierWarnings = results.stream().mapToInt(EvaluationCaseResult::verifierWarningCount).sum();
        int budgetViolationCases = (int) results.stream()
                .filter(result -> result.budgetViolationCount() > 0)
                .count();
        int invalidLinkCases = (int) results.stream()
                .filter(result -> result.invalidLinkCount() > 0)
                .count();

        OptionalDouble judgeAverage = results.stream()
                .filter(result -> result.judgeScores() != null)
                .mapToDouble(result -> result.judgeScores().averageScore())
                .average();

        return new EvaluationMetrics(
                caseCount,
                results.stream().mapToDouble(EvaluationCaseResult::dateCoverageRate).average().orElse(0.0),
                (double) budgetViolationCases / caseCount,
                (double) invalidLinkCases / caseCount,
                citationRequired == 0 ? 1.0 : (double) citationSatisfied / citationRequired,
                results.stream().mapToDouble(EvaluationCaseResult::toolCallSuccessRate).average().orElse(0.0),
                results.stream().mapToLong(EvaluationCaseResult::latencyMs).average().orElse(0.0),
                results.stream().mapToDouble(EvaluationCaseResult::estimatedTokenCostUsd).average().orElse(0.0),
                verifierErrors,
                verifierWarnings,
                judgeAverage.isPresent() ? judgeAverage.getAsDouble() : null
        );
    }

    private int countIssues(
            PlanVerificationResult verification,
            PlanIssueCategory category
    ) {
        return (int) verification.getIssues().stream()
                .filter(issue -> issue.getCategory() == category)
                .count();
    }

    private double dateCoverageRate(ItineraryVerificationRequest request) {
        Set<LocalDate> plannedDates = new HashSet<>();
        for (ItineraryDay day : request.days()) {
            if (day.date() != null) {
                plannedDates.add(day.date());
            }
        }

        int required = 0;
        int covered = 0;
        LocalDate cursor = request.departureDate();
        while (!cursor.isAfter(request.returnDate())) {
            required++;
            if (plannedDates.contains(cursor)) {
                covered++;
            }
            cursor = cursor.plusDays(1);
        }
        return required == 0 ? 0.0 : (double) covered / required;
    }
}
