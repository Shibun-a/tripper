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
