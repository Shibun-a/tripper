package com.embabel.tripper.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("embabel.tripper.observability")
public class AgentRunObservabilityProperties {

    private boolean enabled = true;
    private boolean capturePromptContent = false;
    private int maxSummaryCharacters = 240;
    private int maxRuns = 100;
    // A normal end-to-end run costs ~$0.45 (Chinese/Kimi) to ~$0.52 (English/Claude), so the old
    // 0.15 fired on every run and carried no signal. 0.75 leaves headroom above a normal run and
    // only flags genuine overruns (runaway tool calls, retries). Override per your model/tier.
    private double costWarningThresholdUsd = 0.75;

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
