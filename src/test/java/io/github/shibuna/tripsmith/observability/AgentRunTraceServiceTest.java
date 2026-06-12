package io.github.shibuna.tripsmith.observability;

import io.github.shibuna.tripsmith.safety.SensitiveDataRedactor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRunTraceServiceTest {

    @Test
    void recordsActionTimelineAndFinalUsage() {
        AgentRunTraceService service = service(0.10);
        service.createRun(
                "run-1",
                "Paris to Lyon",
                "Paris",
                "Lyon",
                180.0,
                "briefCharacters=500, travelers=2"
        );

        String eventId = service.startAction(
                "run-1",
                "proposeTravelPlan",
                "poiFindings=4",
                "planner",
                3200,
                List.of("web", "maps")
        );
        service.completeAction("run-1", eventId, "days=3, links=5", 2100);
        service.completeRun("run-1", AgentRunStatus.COMPLETED, 0.12, 1500, 450, List.of("gpt-4.1"));

        AgentRunTrace trace = service.findTrace("run-1").orElseThrow();

        assertEquals(AgentRunStatus.COMPLETED, trace.getStatus());
        assertEquals(1, trace.getCompletedActionCount());
        assertEquals(2, trace.getEstimatedToolCallCount());
        assertEquals(1500, trace.getPromptTokens());
        assertEquals(450, trace.getCompletionTokens());
        assertEquals(List.of("gpt-4.1"), trace.getModelsUsed());
        assertTrue(trace.isHasWarnings());
        assertEquals(1, trace.getEvents().size());
        assertEquals(AgentRunEventStatus.COMPLETED, trace.getEvents().getFirst().getStatus());
        assertNotNull(trace.getEvents().getFirst().getDurationMs());
    }

    @Test
    void recordsFailuresWithoutThrowingFromTraceLayer() {
        AgentRunTraceService service = service(1.0);
        service.createRun("run-2", "Berlin to Munich", "Berlin", "Munich", 180.0, "briefCharacters=300");

        String eventId = service.startAction(
                "run-2",
                "researchPointsOfInterest",
                "pointsOfInterest=5",
                "researcher",
                5000,
                List.of("web")
        );
        service.failAction("run-2", eventId, "tool timeout");

        AgentRunTrace trace = service.findTrace("run-2").orElseThrow();

        assertEquals(1, trace.getFailedActionCount());
        assertEquals("tool timeout", trace.getEvents().getFirst().getErrorMessage());
    }

    @Test
    void storesSummariesInsteadOfPromptBodiesByDefault() {
        AgentRunTraceService service = service(1.0);
        service.createRun(
                "run-3",
                "Tokyo to Kyoto",
                "Tokyo",
                "Kyoto",
                220.0,
                "briefCharacters=1200 email=user@example.com token=abc123"
        );

        String eventId = service.startAction(
                "run-3",
                "proposeTravelPlan",
                "promptCharacters=1200, travelers=2",
                "planner",
                1200,
                List.of("web")
        );
        service.completeAction("run-3", eventId, "htmlCharacters=3000", 3000);

        AgentRunTrace trace = service.findTrace("run-3").orElseThrow();

        assertEquals("briefCharacters=1200 email=[REDACTED_EMAIL] token=[REDACTED]", trace.getInputSummary());
        assertEquals("promptCharacters=1200, travelers=2", trace.getEvents().getFirst().getInputSummary());
        assertFalse(trace.getEvents().getFirst().getInputSummary().contains("Find the best hidden restaurants"));
    }

    @Test
    void readsAreSnapshotsIsolatedFromLaterMutations() {
        AgentRunTraceService service = service(1.0);
        service.createRun("run-4", "A to B", "A", "B", 100.0, "x");
        String eventId = service.startAction("run-4", "findPointsOfInterest", "in", "thinker", 10, List.of());

        AgentRunTrace before = service.findTrace("run-4").orElseThrow();
        service.completeAction("run-4", eventId, "out", 5);

        assertEquals(AgentRunEventStatus.STARTED, before.getEvents().getFirst().getStatus());
        assertEquals(
                AgentRunEventStatus.COMPLETED,
                service.findTrace("run-4").orElseThrow().getEvents().getFirst().getStatus());
    }

    @Test
    void concurrentWritersAndReadersDoNotCorruptTheTimeline() throws Exception {
        AgentRunTraceService service = service(1.0);
        service.createRun("run-5", "A to B", "A", "B", 100.0, "x");
        int writers = 8;
        int eventsPerWriter = 50;

        try (ExecutorService pool = Executors.newFixedThreadPool(writers + 2)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int w = 0; w < writers; w++) {
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < eventsPerWriter; i++) {
                        String id = service.startAction("run-5", "action", "in", null, 1, List.of());
                        service.completeAction("run-5", id, "out", 1);
                    }
                }));
            }
            for (int r = 0; r < 2; r++) {
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < 200; i++) {
                        service.findTrace("run-5")
                                .ifPresent(trace -> trace.getEvents().forEach(AgentRunTraceEvent::getStatus));
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        }

        AgentRunTrace trace = service.findTrace("run-5").orElseThrow();
        assertEquals(writers * eventsPerWriter, trace.getEvents().size());
        assertEquals(writers * eventsPerWriter, trace.getCompletedActionCount());
    }

    private AgentRunTraceService service(double warningThreshold) {
        AgentRunObservabilityProperties properties = new AgentRunObservabilityProperties();
        properties.setCostWarningThresholdUsd(warningThreshold);
        return new AgentRunTraceService(new AgentRunTraceRepository(), properties, new SensitiveDataRedactor());
    }
}
