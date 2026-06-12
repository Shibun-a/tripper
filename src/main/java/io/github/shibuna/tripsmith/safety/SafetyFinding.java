package io.github.shibuna.tripsmith.safety;

public record SafetyFinding(
        SafetyFindingCategory category,
        SafetyRiskLevel riskLevel,
        String message
) {
}
