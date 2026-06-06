package com.embabel.tripper.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("embabel.tripper.observability")
public class AgentRunObservabilityProperties {

    private boolean enabled = true;
    private boolean capturePromptContent = false;
    private int maxSummaryCharacters = 240;
    private int maxRuns = 100;
    private double costWarningThresholdUsd = 0.15;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isCapturePromptContent() {
        return capturePromptContent;
    }

    public void setCapturePromptContent(boolean capturePromptContent) {
        this.capturePromptContent = capturePromptContent;
    }

    public int getMaxSummaryCharacters() {
        return maxSummaryCharacters;
    }

    public void setMaxSummaryCharacters(int maxSummaryCharacters) {
        this.maxSummaryCharacters = maxSummaryCharacters;
    }

    public int getMaxRuns() {
        return maxRuns;
    }

    public void setMaxRuns(int maxRuns) {
        this.maxRuns = maxRuns;
    }

    public double getCostWarningThresholdUsd() {
        return costWarningThresholdUsd;
    }

    public void setCostWarningThresholdUsd(double costWarningThresholdUsd) {
        this.costWarningThresholdUsd = costWarningThresholdUsd;
    }
}
