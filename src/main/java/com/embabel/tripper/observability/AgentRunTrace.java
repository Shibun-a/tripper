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

    public void addEvent(AgentRunTraceEvent event) {
        events.add(event);
        events.sort(Comparator.comparing(AgentRunTraceEvent::getStartedAt));
    }

    public void complete(
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
