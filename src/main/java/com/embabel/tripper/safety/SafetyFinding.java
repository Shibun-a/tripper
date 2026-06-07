package com.embabel.tripper.safety;

public record SafetyFinding(
        SafetyFindingCategory category,
        SafetyRiskLevel riskLevel,
        String message
) {
}
