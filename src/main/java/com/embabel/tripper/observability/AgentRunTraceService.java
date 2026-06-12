package com.embabel.tripper.observability;

import com.embabel.tripper.safety.SensitiveDataRedactor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AgentRunTraceService {

    private final AgentRunTraceRepository repository;
    private final AgentRunObservabilityProperties properties;
    private final SensitiveDataRedactor redactor;

    public AgentRunTraceService(
            AgentRunTraceRepository repository,
            AgentRunObservabilityProperties properties,
            SensitiveDataRedactor redactor
    ) {
        this.repository = repository;
        this.properties = properties;
        this.redactor = redactor;
    }

    public AgentRunTrace createRun(
            String runId,
            String title,
            String fromLocation,
            String toLocation,
            double dailyBudget,
            String inputSummary
    ) {
        if (!properties.isEnabled()) {
            return new AgentRunTrace(runId, title, fromLocation, toLocation, dailyBudget, "observability disabled");
        }
        AgentRunTrace trace = new AgentRunTrace(
                runId,
                sanitize(title),
                sanitize(fromLocation),
                sanitize(toLocation),
                dailyBudget,
                sanitize(inputSummary)
        );
        repository.save(trace);
        repository.pruneToSize(properties.getMaxRuns());
        return trace;
    }

    public String startAction(
            String runId,
            String actionName,
            String inputSummary,
            String modelName,
            Integer promptCharacters,
            List<String> toolNames
    ) {
        if (!properties.isEnabled()) {
            return "";
        }
        AgentRunTraceEvent event = AgentRunTraceEvent.started(
                runId,
                sanitize(actionName),
                sanitize(modelName),
                promptCharacters,
                toolNames == null ? List.of() : toolNames.stream().map(this::sanitize).toList(),
                sanitize(inputSummary)
        );
        repository.appendEvent(runId, event);
        return event.getId();
    }

    public void completeAction(
            String runId,
            String eventId,
            String outputSummary,
            Integer completionCharacters
    ) {
        if (!properties.isEnabled() || eventId == null || eventId.isBlank()) {
            return;
        }
        repository.completeEvent(runId, eventId, sanitize(outputSummary), completionCharacters);
    }

    public void failAction(
            String runId,
            String eventId,
            String errorMessage
    ) {
        if (!properties.isEnabled() || eventId == null || eventId.isBlank()) {
            return;
        }
        repository.failEvent(runId, eventId, sanitize(errorMessage));
    }

    public void completeRun(
            String runId,
            AgentRunStatus status,
            Double costUsd,
            Integer promptTokens,
            Integer completionTokens,
            List<String> modelsUsed
    ) {
        if (!properties.isEnabled()) {
            return;
        }
        repository.completeRun(
                runId,
                status,
                costUsd,
                promptTokens,
                completionTokens,
                modelsUsed == null ? List.of() : modelsUsed.stream().map(this::sanitize).toList(),
                warningsFor(costUsd)
        );
    }

    public Optional<AgentRunTrace> findTrace(String runId) {
        return repository.findByRunId(runId);
    }

    public List<AgentRunTrace> findRecent() {
        return repository.findRecent();
    }

    private List<String> warningsFor(Double costUsd) {
        List<String> warnings = new ArrayList<>();
        if (costUsd != null && costUsd > properties.getCostWarningThresholdUsd()) {
            warnings.add("Cost exceeded configured warning threshold of $" + properties.getCostWarningThresholdUsd());
            warnings.add("Consider switching research-heavy actions to a cheaper model before the next run.");
        }
        return warnings;
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = redactor.redact(value).replaceAll("\\s+", " ").trim();
        if (properties.isCapturePromptContent()) {
            return clip(normalized);
        }
        return clip(normalized);
    }

    private String clip(String value) {
        int max = Math.max(24, properties.getMaxSummaryCharacters());
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 3) + "...";
    }
}
