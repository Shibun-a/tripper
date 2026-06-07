package com.embabel.tripper.safety;

public enum SafetyRiskLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH;

    public boolean atLeast(SafetyRiskLevel other) {
        return ordinal() >= other.ordinal();
    }
}
