package com.embabel.tripper.observability;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory trace store. All mutation happens inside synchronized methods and readers get deep
 * snapshots, so agent threads appending events and web threads rendering /runs never share a
 * mutable trace. The postgres profile swaps in the JPA-backed adapter instead.
 */
@Repository
@Profile("!postgres")
public class AgentRunTraceRepository implements AgentRunTraceStore {

    private final Map<String, AgentRunTrace> traces = new LinkedHashMap<>();

    /** Register a freshly created trace (overwrites a same-id placeholder). */
    @Override
    public synchronized AgentRunTrace save(AgentRunTrace trace) {
        traces.put(trace.getRunId(), trace);
        return trace;
    }

    @Override
    public synchronized void appendEvent(String runId, AgentRunTraceEvent event) {
        traces.computeIfAbsent(runId, AgentRunTrace::unregistered).addEvent(event);
    }

    @Override
    public synchronized void completeEvent(
            String runId,
            String eventId,
            String outputSummary,
            Integer completionCharacters
    ) {
        liveEvent(runId, eventId).ifPresent(event -> event.complete(outputSummary, completionCharacters));
    }

    @Override
    public synchronized void failEvent(String runId, String eventId, String errorMessage) {
        liveEvent(runId, eventId).ifPresent(event -> event.fail(errorMessage));
    }

    @Override
    public synchronized void completeRun(
            String runId,
            AgentRunStatus status,
            Double costUsd,
            Integer promptTokens,
            Integer completionTokens,
            List<String> modelsUsed,
            List<String> warnings
    ) {
        traces.computeIfAbsent(runId, AgentRunTrace::unregistered)
                .complete(status, costUsd, promptTokens, completionTokens, modelsUsed, warnings);
    }

    @Override
    public synchronized Optional<AgentRunTrace> findByRunId(String runId) {
        return Optional.ofNullable(traces.get(runId)).map(AgentRunTrace::snapshot);
    }

    @Override
    public synchronized List<AgentRunTrace> findRecent() {
        return traces.values().stream()
                .sorted(Comparator.comparing(AgentRunTrace::getCreatedAt).reversed())
                .map(AgentRunTrace::snapshot)
                .toList();
    }

    @Override
    public synchronized void pruneToSize(int maxRuns) {
        if (maxRuns <= 0 || traces.size() <= maxRuns) {
            return;
        }
        List<String> oldestRunIds = traces.values().stream()
                .sorted(Comparator.comparing(AgentRunTrace::getCreatedAt))
                .limit(traces.size() - maxRuns)
                .map(AgentRunTrace::getRunId)
                .toList();
        oldestRunIds.forEach(traces::remove);
    }

    @Override
    public synchronized void clear() {
        traces.clear();
    }

    private Optional<AgentRunTraceEvent> liveEvent(String runId, String eventId) {
        AgentRunTrace trace = traces.get(runId);
        if (trace == null) {
            return Optional.empty();
        }
        return trace.getEvents().stream()
                .filter(event -> event.getId().equals(eventId))
                .findFirst();
    }
}
