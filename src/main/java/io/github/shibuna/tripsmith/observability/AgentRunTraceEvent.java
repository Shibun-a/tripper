package io.github.shibuna.tripsmith.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AgentRunTraceEvent {

    private final String id;
    private final String runId;
    private final String actionName;
    private final Instant startedAt;
    private final String modelName;
    private final Integer promptCharacters;
    private final List<String> toolNames;
    private final String inputSummary;
    private AgentRunEventStatus status;
    private Instant completedAt;
    private Long durationMs;
    private String outputSummary;
    private String errorMessage;
    private Integer completionCharacters;

    private AgentRunTraceEvent(
            String id,
            String runId,
            String actionName,
            Instant startedAt,
            String modelName,
            Integer promptCharacters,
            List<String> toolNames,
            String inputSummary
    ) {
        this.id = id;
        this.runId = runId;
        this.actionName = actionName;
        this.startedAt = startedAt;
        this.modelName = modelName;
        this.promptCharacters = promptCharacters;
        this.toolNames = List.copyOf(toolNames == null ? List.of() : toolNames);
        this.inputSummary = inputSummary;
        this.status = AgentRunEventStatus.STARTED;
    }

    public static AgentRunTraceEvent started(
            String runId,
            String actionName,
            String modelName,
            Integer promptCharacters,
            List<String> toolNames,
            String inputSummary
    ) {
        return new AgentRunTraceEvent(
                UUID.randomUUID().toString(),
                runId,
                actionName,
                Instant.now(),
                modelName,
                promptCharacters,
                toolNames,
                inputSummary
        );
    }

    /** Full-state restore used by persistence adapters when rehydrating a stored trace. */
    static AgentRunTraceEvent restore(
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
        AgentRunTraceEvent event = new AgentRunTraceEvent(
                id, runId, actionName, startedAt, modelName, promptCharacters, toolNames, inputSummary);
        event.status = status;
        event.completedAt = completedAt;
        event.durationMs = durationMs;
        event.outputSummary = outputSummary;
        event.errorMessage = errorMessage;
        event.completionCharacters = completionCharacters;
        return event;
    }

    /** Field-for-field copy so repository readers never share a mutable event with writers. */
    AgentRunTraceEvent copySnapshot() {
        AgentRunTraceEvent copy = new AgentRunTraceEvent(
                id, runId, actionName, startedAt, modelName, promptCharacters, toolNames, inputSummary);
        copy.status = status;
        copy.completedAt = completedAt;
        copy.durationMs = durationMs;
        copy.outputSummary = outputSummary;
        copy.errorMessage = errorMessage;
        copy.completionCharacters = completionCharacters;
        return copy;
    }

    public void complete(
            String outputSummary,
            Integer completionCharacters
    ) {
        this.completedAt = Instant.now();
        this.durationMs = Duration.between(startedAt, completedAt).toMillis();
        this.status = AgentRunEventStatus.COMPLETED;
        this.outputSummary = outputSummary;
        this.completionCharacters = completionCharacters;
    }

    public void fail(String errorMessage) {
        this.completedAt = Instant.now();
        this.durationMs = Duration.between(startedAt, completedAt).toMillis();
        this.status = AgentRunEventStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public String getId() {
        return id;
    }

    public String getRunId() {
        return runId;
    }

    public String getActionName() {
        return actionName;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public String getModelName() {
        return modelName;
    }

    public Integer getPromptCharacters() {
        return promptCharacters;
    }

    public List<String> getToolNames() {
        return toolNames;
    }

    public String getToolNamesSummary() {
        return toolNames.isEmpty() ? "none" : String.join(", ", toolNames);
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public AgentRunEventStatus getStatus() {
        return status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Integer getCompletionCharacters() {
        return completionCharacters;
    }
}
