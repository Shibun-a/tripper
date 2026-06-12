package com.embabel.tripper.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AgentRunTrace {

    private final String runId;
    private final Instant createdAt;
    private final String title;
    private final String fromLocation;
    private final String toLocation;
    private final double dailyBudget;
    private final String inputSummary;
    private final List<AgentRunTraceEvent> events = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private AgentRunStatus status = AgentRunStatus.RUNNING;
    private Instant completedAt;
    private Long totalDurationMs;
    private Double costUsd;
    private Integer promptTokens;
    private Integer completionTokens;
    private List<String> modelsUsed = List.of();

    public AgentRunTrace(
            String runId,
            String title,
            String fromLocation,
            String toLocation,
            double dailyBudget,
            String inputSummary
    ) {
        this.runId = runId;
        this.title = title;
        this.fromLocation = fromLocation;
        this.toLocation = toLocation;
        this.dailyBudget = dailyBudget;
        this.inputSummary = inputSummary;
        this.createdAt = Instant.now();
    }

    private AgentRunTrace(AgentRunTrace source) {
        this.runId = source.runId;
        this.createdAt = source.createdAt;
        this.title = source.title;
        this.fromLocation = source.fromLocation;
        this.toLocation = source.toLocation;
        this.dailyBudget = source.dailyBudget;
        this.inputSummary = source.inputSummary;
        source.events.forEach(event -> this.events.add(event.copySnapshot()));
        this.warnings.addAll(source.warnings);
        this.status = source.status;
        this.completedAt = source.completedAt;
        this.totalDurationMs = source.totalDurationMs;
        this.costUsd = source.costUsd;
        this.promptTokens = source.promptTokens;
        this.completionTokens = source.completionTokens;
        this.modelsUsed = source.modelsUsed;
    }

    /** Full-state restore used by persistence adapters when rehydrating a stored trace. */
    AgentRunTrace(
            String runId,
            Instant createdAt,
            String title,
            String fromLocation,
            String toLocation,
            double dailyBudget,
            String inputSummary,
            List<AgentRunTraceEvent> events,
            List<String> warnings,
            AgentRunStatus status,
            Instant completedAt,
            Long totalDurationMs,
            Double costUsd,
            Integer promptTokens,
            Integer completionTokens,
            List<String> modelsUsed
    ) {
        this.runId = runId;
        this.createdAt = createdAt;
        this.title = title;
        this.fromLocation = fromLocation;
        this.toLocation = toLocation;
        this.dailyBudget = dailyBudget;
        this.inputSummary = inputSummary;
        this.events.addAll(events == null ? List.of() : events);
        this.warnings.addAll(warnings == null ? List.of() : warnings);
        this.status = status;
        this.completedAt = completedAt;
        this.totalDurationMs = totalDurationMs;
        this.costUsd = costUsd;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.modelsUsed = modelsUsed == null ? List.of() : List.copyOf(modelsUsed);
    }

    /** Placeholder for events arriving before the web layer registered the run. */
    static AgentRunTrace unregistered(String runId) {
        return new AgentRunTrace(
                runId,
                "Unregistered agent run",
                "unknown",
                "unknown",
                0.0,
                "trace started from agent action"
        );
    }

    /**
     * Deep copy for readers. Live traces are mutated under the repository lock while agent
     * actions run; handing out copies keeps render-time iteration safe without sharing the lock.
     */
    AgentRunTrace snapshot() {
        return new AgentRunTrace(this);
    }

    void addEvent(AgentRunTraceEvent event) {
        events.add(event);
        events.sort(Comparator.comparing(AgentRunTraceEvent::getStartedAt));
    }

    void complete(
            AgentRunStatus status,
            Double costUsd,
            Integer promptTokens,
            Integer completionTokens,
            List<String> modelsUsed,
            List<String> warnings
    ) {
        this.status = status;
        this.completedAt = Instant.now();
        this.totalDurationMs = Duration.between(createdAt, completedAt).toMillis();
        this.costUsd = costUsd;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.modelsUsed = List.copyOf(modelsUsed == null ? List.of() : modelsUsed);
        this.warnings.clear();
        this.warnings.addAll(warnings == null ? List.of() : warnings);
    }

    public String getRunId() {
        return runId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getTitle() {
        return title;
    }

    public String getFromLocation() {
        return fromLocation;
    }

    public String getToLocation() {
        return toLocation;
    }

    public double getDailyBudget() {
        return dailyBudget;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public List<AgentRunTraceEvent> getEvents() {
        return List.copyOf(events);
    }

    public AgentRunStatus getStatus() {
        return status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Long getTotalDurationMs() {
        return totalDurationMs;
    }

    public Double getCostUsd() {
        return costUsd;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public List<String> getModelsUsed() {
        return modelsUsed;
    }

    public String getModelsUsedSummary() {
        return modelsUsed.isEmpty() ? "n/a" : String.join(", ", modelsUsed);
    }

    public List<String> getWarnings() {
        return List.copyOf(warnings);
    }

    public boolean isHasWarnings() {
        return !warnings.isEmpty();
    }

    public int getCompletedActionCount() {
        return (int) events.stream()
                .filter(event -> event.getStatus() == AgentRunEventStatus.COMPLETED)
                .count();
    }

    public int getFailedActionCount() {
        return (int) events.stream()
                .filter(event -> event.getStatus() == AgentRunEventStatus.FAILED)
                .count();
    }

    public int getEstimatedToolCallCount() {
        return events.stream().mapToInt(event -> event.getToolNames().size()).sum();
    }
}
