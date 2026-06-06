package com.embabel.tripper.observability;

import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class AgentRunTraceRepository {

    private final Map<String, AgentRunTrace> traces = new LinkedHashMap<>();

    public synchronized AgentRunTrace save(AgentRunTrace trace) {
        traces.put(trace.getRunId(), trace);
        return trace;
    }

    public synchronized Optional<AgentRunTrace> findByRunId(String runId) {
        return Optional.ofNullable(traces.get(runId));
    }

    public synchronized List<AgentRunTrace> findRecent() {
        return traces.values().stream()
                .sorted(Comparator.comparing(AgentRunTrace::getCreatedAt).reversed())
                .toList();
    }

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

    public synchronized void clear() {
        traces.clear();
    }
}
