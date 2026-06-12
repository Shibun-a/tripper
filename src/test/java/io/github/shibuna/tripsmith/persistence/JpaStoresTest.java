package io.github.shibuna.tripsmith.persistence;

import io.github.shibuna.tripsmith.editing.EditableItineraryDay;
import io.github.shibuna.tripsmith.editing.JpaPlanEditSessionStore;
import io.github.shibuna.tripsmith.editing.PlanEditDiff;
import io.github.shibuna.tripsmith.editing.PlanEditSession;
import io.github.shibuna.tripsmith.editing.PlanEditSessionJpaRepository;
import io.github.shibuna.tripsmith.editing.PlanEditVersion;
import io.github.shibuna.tripsmith.observability.AgentRunStatus;
import io.github.shibuna.tripsmith.observability.AgentRunTrace;
import io.github.shibuna.tripsmith.observability.AgentRunTraceEvent;
import io.github.shibuna.tripsmith.observability.AgentRunTraceJpaRepository;
import io.github.shibuna.tripsmith.observability.JpaAgentRunTraceStore;
import io.github.shibuna.tripsmith.rag.JpaTravelKnowledgeDocumentStore;
import io.github.shibuna.tripsmith.rag.TravelKnowledgeDocument;
import io.github.shibuna.tripsmith.rag.TravelKnowledgeDocumentJpaRepository;
import io.github.shibuna.tripsmith.rag.TravelKnowledgeSourceType;
import io.github.shibuna.tripsmith.verification.JpaPlanVerificationResultStore;
import io.github.shibuna.tripsmith.verification.PlanIssueCategory;
import io.github.shibuna.tripsmith.verification.PlanVerificationIssue;
import io.github.shibuna.tripsmith.verification.PlanVerificationResult;
import io.github.shibuna.tripsmith.verification.PlanVerificationResultJpaRepository;
import io.github.shibuna.tripsmith.verification.VerificationSeverity;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trips every JPA store against an embedded H2 database: the same adapter code that runs
 * on Postgres, exercised without Docker so CI stays self-contained. The pgvector path is
 * verified manually via the compose postgres service (see LOCAL-DEVELOPMENT.md).
 *
 * <p>The mapper below mirrors the relevant Spring Boot ObjectMapper defaults the stores rely
 * on (parameter-names creators, java.time, tolerant deserialization).
 */
@DataJpaTest
@ActiveProfiles("postgres")
@ContextConfiguration(classes = JpaStoresTest.TestConfig.class)
class JpaStoresTest {

    // No @EnableAutoConfiguration here: the @DataJpaTest slice supplies the JPA/datasource
    // auto-configuration, and pulling in full auto-configuration would boot the agent platform.
    @Configuration(proxyBeanMethods = false)
    @EnableJpaRepositories(basePackages = "io.github.shibuna.tripsmith")
    @EntityScan(basePackages = "io.github.shibuna.tripsmith")
    static class TestConfig {
    }

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .addModule(new ParameterNamesModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Autowired
    private AgentRunTraceJpaRepository traceJpaRepository;

    @Autowired
    private PlanEditSessionJpaRepository sessionJpaRepository;

    @Autowired
    private TravelKnowledgeDocumentJpaRepository documentJpaRepository;

    @Autowired
    private PlanVerificationResultJpaRepository verificationJpaRepository;

    @Test
    void traceLifecycleRoundTripsThroughTheDatabase() {
        JpaAgentRunTraceStore store = new JpaAgentRunTraceStore(traceJpaRepository, mapper);

        store.save(new AgentRunTrace("run-1", "Paris to Lyon", "Paris", "Lyon", 180.0, "brief"));
        AgentRunTraceEvent event = AgentRunTraceEvent.started(
                "run-1", "proposeTravelPlan", "planner", 3200, List.of("web", "maps"), "poiFindings=4");
        store.appendEvent("run-1", event);
        store.completeEvent("run-1", event.getId(), "days=3", 2100);
        store.completeRun("run-1", AgentRunStatus.COMPLETED, 0.42, 1500, 450, List.of("claude"), List.of());

        AgentRunTrace loaded = store.findByRunId("run-1").orElseThrow();
        assertEquals(AgentRunStatus.COMPLETED, loaded.getStatus());
        assertEquals(0.42, loaded.getCostUsd());
        assertEquals(1, loaded.getEvents().size());
        assertEquals("days=3", loaded.getEvents().getFirst().getOutputSummary());
        assertEquals(1, loaded.getCompletedActionCount());

        store.save(new AgentRunTrace("run-2", "A to B", "A", "B", 100.0, "x"));
        store.pruneToSize(1);
        assertEquals(1, store.findRecent().size());
        assertTrue(store.findByRunId("run-1").isEmpty());
    }

    @Test
    void appendingToAnUnknownRunCreatesAPlaceholderTrace() {
        JpaAgentRunTraceStore store = new JpaAgentRunTraceStore(traceJpaRepository, mapper);
        AgentRunTraceEvent event = AgentRunTraceEvent.started(
                "ghost-run", "findPointsOfInterest", null, 10, List.of(), "in");

        store.appendEvent("ghost-run", event);

        AgentRunTrace loaded = store.findByRunId("ghost-run").orElseThrow();
        assertEquals("Unregistered agent run", loaded.getTitle());
        assertEquals(1, loaded.getEvents().size());
    }

    @Test
    void editSessionWithVersionsAndVerifierResultRoundTrips() {
        JpaPlanEditSessionStore store = new JpaPlanEditSessionStore(sessionJpaRepository, mapper);
        PlanEditSession session = new PlanEditSession(
                "run-9", "Trip", "Paris", "Beaune", "driving",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3), 200.0, "keep it relaxed");
        PlanVerificationResult verification = new PlanVerificationResult(
                "ver-1", Instant.now(),
                List.of(PlanVerificationIssue.of(
                        PlanIssueCategory.BUDGET_EXCEEDED, VerificationSeverity.WARNING, "too pricey")),
                List.of(), false, 0);
        session.addVersion(new PlanEditVersion(
                1, Instant.now(), "Original generated plan", "<h4>Plan</h4>",
                List.of(new EditableItineraryDay(LocalDate.of(2026, 7, 1), "Paris,+France", null)),
                new PlanEditDiff("Original version.", List.of()),
                verification));
        store.save(session);

        PlanEditSession loaded = store.findByRunId("run-9").orElseThrow();
        assertEquals("Trip", loaded.getTitle());
        assertEquals(1, loaded.getVersions().size());
        PlanEditVersion version = loaded.latestVersion();
        assertEquals("<h4>Plan</h4>", version.planHtml());
        assertEquals("Paris", version.days().getFirst().stayingAt());
        assertEquals(PlanIssueCategory.BUDGET_EXCEEDED,
                version.verificationResult().getIssues().getFirst().getCategory());
    }

    @Test
    void knowledgeDocumentsAndChunkIdsRoundTrip() {
        JpaTravelKnowledgeDocumentStore store = new JpaTravelKnowledgeDocumentStore(documentJpaRepository, mapper);
        store.save(new TravelKnowledgeDocument(
                "doc-1", "Wine notes", TravelKnowledgeSourceType.PASTED_TEXT, "manual", "Bordeaux is lovely."),
                List.of("chunk-a", "chunk-b"));

        List<TravelKnowledgeDocument> documents = store.findAllDocuments();
        assertEquals(1, documents.size());
        assertEquals("Wine notes", documents.getFirst().getTitle());
        assertEquals(List.of("chunk-a", "chunk-b"), store.allChunkIds());

        store.clear();
        assertTrue(store.findAllDocuments().isEmpty());
    }

    @Test
    void verificationResultsRoundTripAndStayBounded() {
        JpaPlanVerificationResultStore store = new JpaPlanVerificationResultStore(verificationJpaRepository, mapper);
        PlanVerificationResult result = new PlanVerificationResult(
                "ver-2", Instant.now(),
                List.of(PlanVerificationIssue.onDate(
                        PlanIssueCategory.DATE_GAP, VerificationSeverity.ERROR,
                        LocalDate.of(2026, 7, 2), "missing day")),
                List.of(), true, 1);
        store.save(result);

        PlanVerificationResult loaded = store.findById("ver-2").orElseThrow();
        assertTrue(loaded.isHasErrors());
        assertTrue(loaded.isRepaired());
        assertEquals(1, loaded.getRepairAttempts());
        assertEquals(LocalDate.of(2026, 7, 2), loaded.getIssues().getFirst().getDate());
        assertEquals(1, store.findRecent().size());
    }
}
