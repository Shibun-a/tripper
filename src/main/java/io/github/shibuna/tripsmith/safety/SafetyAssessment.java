package io.github.shibuna.tripsmith.safety;

import java.util.List;
import java.util.stream.Collectors;

public final class SafetyAssessment {

    private final String sourceLabel;
    private final SafetyRiskLevel riskLevel;
    private final List<SafetyFinding> findings;

    public SafetyAssessment(
            String sourceLabel,
            SafetyRiskLevel riskLevel,
            List<SafetyFinding> findings
    ) {
        this.sourceLabel = sourceLabel;
        this.riskLevel = riskLevel;
        this.findings = List.copyOf(findings == null ? List.of() : findings);
    }

    public static SafetyAssessment safe(String sourceLabel) {
        return new SafetyAssessment(sourceLabel, SafetyRiskLevel.NONE, List.of());
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public SafetyRiskLevel getRiskLevel() {
        return riskLevel;
    }

    public List<SafetyFinding> getFindings() {
        return findings;
    }

    public boolean isHasFindings() {
        return !findings.isEmpty();
    }

    public boolean isHighRisk() {
        return riskLevel.atLeast(SafetyRiskLevel.HIGH);
    }

    public String getSummary() {
        if (findings.isEmpty()) {
            return "No safety findings.";
        }
        return findings.stream()
                .map(finding -> finding.riskLevel() + " " + finding.category() + ": " + finding.message())
                .collect(Collectors.joining("; "));
    }
}
