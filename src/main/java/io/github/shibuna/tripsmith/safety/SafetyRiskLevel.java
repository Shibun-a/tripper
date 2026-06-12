package io.github.shibuna.tripsmith.safety;

public enum SafetyRiskLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH;

    public boolean atLeast(SafetyRiskLevel other) {
        return ordinal() >= other.ordinal();
    }
}
