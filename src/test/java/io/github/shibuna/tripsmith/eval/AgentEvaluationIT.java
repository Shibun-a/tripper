package io.github.shibuna.tripsmith.eval;

import io.github.shibuna.tripsmith.verification.ItineraryVerificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real, agent-backed evaluation over a small curated dataset, scored by the deterministic verifier
 * AND the LLM judge. This is the trustworthy tier that produces resume-grade numbers — and doubles
 * as the project's first end-to-end agent-flow integration test.
 *
 * <p>It costs money and needs the full stack (Anthropic key + MCP gateway on :9011), so it is gated
 * behind the {@code EVAL_AGENT=true} environment variable and skipped in normal CI. Run it with:
 * <pre>
 *   set -a &amp;&amp; . ./.env.local &amp;&amp; set +a &amp;&amp; EVAL_AGENT=true ./mvnw test -Dtest=AgentEvaluationIT
 * </pre>
 * Beans are injected via their Java interfaces ({@link EvalPlanCandidateFactory}, {@link PlanJudge})
 * so this Java test never references the Kotlin implementations directly.
 */
@SpringBootTest
@ActiveProfiles("gateway")
@EnabledIfEnvironmentVariable(named = "EVAL_AGENT", matches = "true")
class AgentEvaluationIT {

    private static final Path AGENT_DATASET = Path.of("evals", "travel-eval-cases-agent.json");

    @Autowired
    private EvalPlanCandidateFactory candidateFactory;

    @Autowired
    private ItineraryVerificationService verificationService;

    @Autowired
    private PlanJudge judge;

    @Test
    void runsRealAgentEvaluationWithJudge() throws Exception {
        List<TravelEvalCase> cases = TravelEvalDataset.load(AGENT_DATASET);
        TravelEvaluationHarness harness = new TravelEvaluationHarness(candidateFactory, verificationService, judge);

        EvaluationReport report = harness.run(cases, AGENT_DATASET.toString(), 0);
        TravelEvaluationReportWriter.write(report, Path.of("target", "evals"));

        // Sanity, not exactness (real LLM output is non-deterministic).
        assertEquals(cases.size(), report.metrics().caseCount());
        // completeDays() guarantees full date coverage, so a healthy run stays near 1.0.
        assertTrue(report.metrics().dateCoverageRate() > 0.9,
                "date coverage too low: " + report.metrics().dateCoverageRate());
        assertNotNull(report.metrics().averageJudgeOverall(), "judge produced no scores");
        double judgeAvg = report.metrics().averageJudgeOverall();
        assertTrue(judgeAvg >= 1.0 && judgeAvg <= 5.0, "judge average out of range: " + judgeAvg);
    }
}
