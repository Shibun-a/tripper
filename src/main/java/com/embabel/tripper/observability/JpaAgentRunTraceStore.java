package com.embabel.tripper.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Database-backed trace store (postgres profile). Mutations are read-modify-write cycles on a
 * row-locked entity inside a transaction; readers rehydrate fresh domain objects, which are
 * snapshots by construction.
 */
@Repository
@Profile("postgres")
@Transactional
public class JpaAgentRunTraceStore implements AgentRunTraceStore {

    private final AgentRunTraceJpaRepository repository;
    private final ObjectMapper objectMapper;

    public JpaAgentRunTraceStore(AgentRunTraceJpaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentRunTrace save(AgentRunTrace trace) {
        repository.save(toEntity(trace));
        return trace;
    }

    @Override
    public void appendEvent(String runId, AgentRunTraceEvent event) {
        mutate(runId, trace -> trace.addEvent(event));
    }

    @Override
    public void completeEvent(String runId, String eventId, String outputSummary, Integer completionCharacters) {
        mutate(runId, trace -> liveEvent(trace, eventId)
                .ifPresent(event -> event.complete(outputSummary, completionCharacters)));
    }

    @Override
    public void failEvent(String runId, String eventId, String errorMessage) {
        mutate(runId, trace -> liveEvent(trace, eventId).ifPresent(event -> event.fail(errorMessage)));
    }

    @Override
    public void completeRun(
            String runId,
            AgentRunStatus status,
            Double costUsd,
            Integer promptTokens,
            Integer completionTokens,
            List<String> modelsUsed,
            List<String> warnings
    ) {
        mutate(runId, trace -> trace.complete(status, costUsd, promptTokens, completionTokens, modelsUsed, warnings));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentRunTrace> findByRunId(String runId) {
        return repository.findById(runId).map(this::toTrace);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentRunTrace> findRecent() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toTrace).toList();
    }

    @Override
    public void pruneToSize(int maxRuns) {
        if (maxRuns <= 0) {
            return;
        }
        List<AgentRunTraceEntity> oldestFirst = repository.findAllByOrderByCreatedAtAsc();
        int excess = oldestFirst.size() - maxRuns;
        if (excess > 0) {
            repository.deleteAll(oldestFirst.subList(0, excess));
        }
    }

    @Override
    public void clear() {
        repository.deleteAll();
    }

    private void mutate(String runId, java.util.function.Consumer<AgentRunTrace> mutation) {
        Optional<AgentRunTraceEntity> existing = repository.findForUpdate(runId);
        AgentRunTrace trace = existing.map(this::toTrace).orElseGet(() -> AgentRunTrace.unregistered(runId));
        mutation.accept(trace);
        if (existing.isPresent()) {
            existing.get().update(trace.getStatus(), trace.getCostUsd(), writePayload(TraceDoc.of(trace)));
        } else {
            repository.save(toEntity(trace));
        }
    }

    private Optional<AgentRunTraceEvent> liveEvent(AgentRunTrace trace, String eventId) {
        // getEvents copies the list but not the events, so completing one mutates the trace.
        return trace.getEvents().stream().filter(event -> event.getId().equals(eventId)).findFirst();
    }

    private AgentRunTraceEntity toEntity(AgentRunTrace trace) {
        return new AgentRunTraceEntity(
                trace.getRunId(),
                trace.getCreatedAt(),
                trace.getStatus(),
                trace.getCostUsd(),
                writePayload(TraceDoc.of(trace))
        );
    }

    private AgentRunTrace toTrace(AgentRunTraceEntity entity) {
        try {
            return objectMapper.readValue(entity.getPayload(), TraceDoc.class).toTrace();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot read stored trace " + entity.getRunId(), ex);
        }
    }

    private String writePayload(TraceDoc doc) {
        try {
            return objectMapper.writeValueAsString(doc);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize trace " + doc.runId(), ex);
        }
    }

    record TraceDoc(
            String runId,
            Instant createdAt,
            String title,
            String fromLocation,
            String toLocation,
            double dailyBudget,
            String inputSummary,
            List<EventDoc> events,
            List<String> warnings,
            AgentRunStatus status,
            Instant completedAt,
            Long totalDurationMs,
            Double costUsd,
            Integer promptTokens,
            Integer completionTokens,
            List<String> modelsUsed
    ) {
        static TraceDoc of(AgentRunTrace trace) {
            return new TraceDoc(
                    trace.getRunId(),
                    trace.getCreatedAt(),
                    trace.getTitle(),
                    trace.getFromLocation(),
                    trace.getToLocation(),
                    trace.getDailyBudget(),
                    trace.getInputSummary(),
                    trace.getEvents().stream().map(EventDoc::of).toList(),
                    trace.getWarnings(),
                    trace.getStatus(),
                    trace.getCompletedAt(),
                    trace.getTotalDurationMs(),
                    trace.getCostUsd(),
                    trace.getPromptTokens(),
                    trace.getCompletionTokens(),
                    trace.getModelsUsed()
            );
        }

        AgentRunTrace toTrace() {
            return new AgentRunTrace(
                    runId,
                    createdAt,
                    title,
                    fromLocation,
                    toLocation,
                    dailyBudget,
                    inputSummary,
                    events == null ? List.of() : events.stream().map(EventDoc::toEvent).toList(),
                    warnings,
                    status,
                    completedAt,
                    totalDurationMs,
                    costUsd,
                    promptTokens,
                    completionTokens,
                    modelsUsed
            );
        }
    }

    record EventDoc(
            String id,
            String runId,
            String actionName,
            Instant startedAt,
            String modelName,
            Integer promptCharacters,
            List<String> toolNames,
            String inputSummary,
            AgentRunEventStatus status,
            Instant completedAt,
            Long durationMs,
            String outputSummary,
            String errorMessage,
            Integer completionCharacters
    ) {
        static EventDoc of(AgentRunTraceEvent event) {
            return new EventDoc(
                    event.getId(),
                    event.getRunId(),
                    event.getActionName(),
                    event.getStartedAt(),
                    event.getModelName(),
                    event.getPromptCharacters(),
                    event.getToolNames(),
                    event.getInputSummary(),
                    event.getStatus(),
                    event.getCompletedAt(),
                    event.getDurationMs(),
                    event.getOutputSummary(),
                    event.getErrorMessage(),
                    event.getCompletionCharacters()
            );
        }

        AgentRunTraceEvent toEvent() {
            return AgentRunTraceEvent.restore(
                    id, runId, actionName, startedAt, modelName, promptCharacters, toolNames, inputSummary,
                    status, completedAt, durationMs, outputSummary, errorMessage, completionCharacters
            );
        }
    }
}
