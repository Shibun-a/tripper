package io.github.shibuna.tripsmith.verification;

import com.embabel.common.ai.prompt.PromptContributor;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

public final class PlanVerificationResult implements PromptContributor {

    private final String id;
    private final Instant createdAt;
    private final List<PlanVerificationIssue> issues;
    private final List<TravelLegEstimate> routeEstimates;
    private final boolean repaired;
    private final int repairAttempts;

    public PlanVerificationResult(
            String id,
            Instant createdAt,
            List<PlanVerificationIssue> issues,
            List<TravelLegEstimate> routeEstimates,
            boolean repaired,
            int repairAttempts
    ) {
        this.id = id;
        this.createdAt = createdAt;
        this.issues = List.copyOf(issues);
        this.routeEstimates = List.copyOf(routeEstimates);
        this.repaired = repaired;
        this.repairAttempts = repairAttempts;
    }

    public static PlanVerificationResult empty() {
        return new PlanVerificationResult(
                "not-run",
                Instant.EPOCH,
                List.of(),
                List.of(),
                false,
                0
        );
    }

    public String getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<PlanVerificationIssue> getIssues() {
        return issues;
    }

    public List<TravelLegEstimate> getRouteEstimates() {
        return routeEstimates;
    }

    public boolean isRepaired() {
        return repaired;
    }

    public int getRepairAttempts() {
        return repairAttempts;
    }

    public boolean isHasIssues() {
        return !issues.isEmpty();
    }

    public boolean isHasErrors() {
        return issues.stream().anyMatch(issue -> issue.getSeverity() == VerificationSeverity.ERROR);
    }

    public boolean isHasWarnings() {
        return issues.stream().anyMatch(issue -> issue.getSeverity() == VerificationSeverity.WARNING);
    }

    public long getErrorCount() {
        return issues.stream().filter(issue -> issue.getSeverity() == VerificationSeverity.ERROR).count();
    }

    public long getWarningCount() {
        return issues.stream().filter(issue -> issue.getSeverity() == VerificationSeverity.WARNING).count();
    }

    public long getInfoCount() {
        return issues.stream().filter(issue -> issue.getSeverity() == VerificationSeverity.INFO).count();
    }

    public String getStatus() {
        if (isHasErrors()) {
            return "FAILED";
        }
        if (isHasWarnings()) {
            return "PASSED_WITH_WARNINGS";
        }
        return "PASSED";
    }

    public String getSummary() {
        if (!isHasIssues()) {
            return "Verification passed with no issues.";
        }
        return "Verification " + getStatus()
                + ": " + getErrorCount() + " error(s), "
                + getWarningCount() + " warning(s), "
                + getInfoCount() + " info item(s).";
    }

    @Override
    public String contribution() {
        if (!isHasIssues()) {
            return "The itinerary verifier found no issues.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("The itinerary verifier found structured issues that must be addressed.\n");
        sb.append("Status: ").append(getStatus()).append('\n');
        sb.append("Issues:\n");
        for (PlanVerificationIssue issue : issues) {
            sb.append(issue.getPromptLine()).append('\n');
        }

        if (!routeEstimates.isEmpty()) {
            sb.append("Route estimates:\n");
            for (TravelLegEstimate estimate : routeEstimates) {
                sb.append("- ")
                        .append(estimate.getFromDate()).append(" ")
                        .append(estimate.getFromLocation()).append(" -> ")
                        .append(estimate.getToDate()).append(" ")
                        .append(estimate.getToLocation());
                if (estimate.getDistanceKm() != null) {
                    sb.append(" approx ").append(Math.round(estimate.getDistanceKm())).append(" km");
                }
                if (estimate.getEstimatedHours() != null) {
                    sb.append(", ").append(String.format(Locale.ROOT, "%.1f", estimate.getEstimatedHours())).append(" h");
                }
                sb.append(" via ").append(estimate.getMethod()).append('\n');
            }
        }
        return sb.toString().trim();
    }
}
