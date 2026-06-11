package com.embabel.tripper.eval;

import com.embabel.tripper.verification.ItineraryVerificationService;
import com.embabel.tripper.verification.PlanVerificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelEvaluationHarnessTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsDatasetWithPortfolioCoverage() throws Exception {
        List<TravelEvalCase> cases = TravelEvalDataset.loadDefault();

        assertTrue(cases.size() >= 30);
        assertTrue(cases.size() <= 50);
        assertTrue(cases.stream().anyMatch(evalCase -> contains(evalCase.constraints(), "strict budget")));
        assertTrue(cases.stream().anyMatch(evalCase -> contains(evalCase.expectedThemes(), "family")));
        assertTrue(cases.stream().anyMatch(evalCase -> contains(evalCase.expectedThemes(), "accessibility")));
        assertTrue(cases.stream().anyMatch(TravelEvalCase::requiresKnowledgeCitation));
        assertTrue(cases.stream().anyMatch(evalCase -> evalCase.expectedCountries().size() > 1));
        assertTrue(cases.stream().anyMatch(evalCase -> contains(evalCase.expectedThemes(), "food")));
        assertTrue(cases.stream().anyMatch(evalCase -> contains(evalCase.expectedThemes(), "history")));
        assertTrue(cases.stream().anyMatch(evalCase -> contains(evalCase.expectedThemes(), "nature")));
    }

    @Test
    void runsLightweightRegressionSubset() throws Exception {
        EvaluationReport report = harness().run(
                TravelEvalDataset.loadDefault(),
                TravelEvalDataset.DEFAULT_PATH.toString(),
                8
        );

        EvaluationMetrics metrics = report.metrics();
        assertEquals(8, metrics.caseCount());
        assertEquals(1.0, metrics.dateCoverageRate());
        assertEquals(0.0, metrics.budgetViolationRate());
        assertEquals(0.0, metrics.invalidLinkRate());
        assertEquals(1.0, metrics.citationCoverageRate());
        assertEquals(1.0, metrics.toolCallSuccessRate());
        assertEquals(0, metrics.verifierErrorCount());
        assertFalse(report.toMarkdown().isBlank());
    }

    @Test
    void writesJsonAndMarkdownReports() throws Exception {
        EvaluationReport report = harness().run(
                TravelEvalDataset.loadDefault(),
                TravelEvalDataset.DEFAULT_PATH.toString(),
                5
        );

        TravelEvaluationReportWriter.WrittenReports writtenReports = TravelEvaluationReportWriter.write(
                report,
                tempDir
        );

        assertTrue(Files.exists(writtenReports.jsonPath()));
        assertTrue(Files.exists(writtenReports.markdownPath()));
        assertTrue(Files.readString(writtenReports.jsonPath()).contains("\"caseCount\" : 5"));
        assertTrue(Files.readString(writtenReports.markdownPath()).contains("Travel Agent Evaluation Report"));
    }

    @Test
    void threadsJudgeScoresIntoResultsAndMetrics() throws Exception {
        // Fake judge (functional interface) returns a fixed score; proves the harness wires judge
        // output into per-case results, the aggregate average, and the markdown — no LLM/network.
        PlanJudge fakeJudge = (planText, interests, constraints, themes, countries) ->
                new JudgeScores(4, 4, 4, 4, 4, "fixed test score");
        TravelEvaluationHarness harness = new TravelEvaluationHarness(
                new DeterministicEvalPlanCandidateFactory(),
                new ItineraryVerificationService(new PlanVerificationRepository()),
                fakeJudge
        );

        EvaluationReport report = harness.run(
                TravelEvalDataset.loadDefault(),
                TravelEvalDataset.DEFAULT_PATH.toString(),
                3
        );

        assertEquals(3, report.metrics().caseCount());
        assertEquals(4.0, report.metrics().averageJudgeOverall());
        assertTrue(report.cases().stream().allMatch(result -> result.judgeScores() != null));
        assertEquals(4.0, report.cases().get(0).judgeScores().averageScore());
        String markdown = report.toMarkdown();
        assertTrue(markdown.contains("Avg judge score"));
        assertTrue(markdown.contains("Judge (1-5)"));
    }

    @Test
    void leavesJudgeNullWhenNoJudgeConfigured() throws Exception {
        EvaluationReport report = harness().run(
                TravelEvalDataset.loadDefault(),
                TravelEvalDataset.DEFAULT_PATH.toString(),
                3
        );

        assertNull(report.metrics().averageJudgeOverall());
        assertTrue(report.cases().stream().allMatch(result -> result.judgeScores() == null));
        assertFalse(report.toMarkdown().contains("Avg judge score"));
    }

    private TravelEvaluationHarness harness() {
        return new TravelEvaluationHarness(
                new DeterministicEvalPlanCandidateFactory(),
                new ItineraryVerificationService(new PlanVerificationRepository())
        );
    }

    private boolean contains(
            List<String> values,
            String expected
    ) {
        return values.stream().anyMatch(value -> value.toLowerCase().contains(expected));
    }
}
