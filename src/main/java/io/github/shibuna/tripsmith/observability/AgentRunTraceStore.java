package io.github.shibuna.tripsmith.observability;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for agent run traces. All mutation goes through the store so locking (or the
 * database transaction) stays an implementation concern; readers always receive snapshots that
 * are safe to render while a run is still appending events.
 */
public interface AgentRunTraceStore {

    /** Register a freshly created trace (overwrites a same-id placeholder). */
    AgentRunTrace save(AgentRunTrace trace);

    void appendEvent(String runId, AgentRunTraceEvent event);

    void completeEvent(String runId, String eventId, String outputSummary, Integer completionCharacters);

    void failEvent(String runId, String eventId, String errorMessage);

    void completeRun(
            String runId,
            AgentRunStatus status,
            Double costUsd,
            Integer promptTokens,
            Integer completionTokens,
            List<String> modelsUsed,
            List<String> warnings
    );

    Optional<AgentRunTrace> findByRunId(String runId);

    List<AgentRunTrace> findRecent();

    void pruneToSize(int maxRuns);

    void clear();
}
