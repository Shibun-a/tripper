package io.github.shibuna.tripsmith.verification;

import java.time.LocalDate;

public final class PlanVerificationIssue {

    private final PlanIssueCategory category;
    private final VerificationSeverity severity;
    private final String message;
    private final LocalDate date;
    private final String location;
    private final String details;

    public PlanVerificationIssue(
            PlanIssueCategory category,
            VerificationSeverity severity,
            String message,
            LocalDate date,
            String location,
            String details
    ) {
        this.category = category;
        this.severity = severity;
        this.message = message;
        this.date = date;
        this.location = location;
        this.details = details;
    }

    public static PlanVerificationIssue of(
            PlanIssueCategory category,
            VerificationSeverity severity,
            String message
    ) {
        return new PlanVerificationIssue(category, severity, message, null, null, null);
    }

    public static PlanVerificationIssue onDate(
            PlanIssueCategory category,
            VerificationSeverity severity,
            LocalDate date,
            String message
    ) {
        return new PlanVerificationIssue(category, severity, message, date, null, null);
    }

    public static PlanVerificationIssue atLocation(
            PlanIssueCategory category,
            VerificationSeverity severity,
            LocalDate date,
            String location,
            String message,
            String details
    ) {
        return new PlanVerificationIssue(category, severity, message, date, location, details);
    }

    public PlanIssueCategory getCategory() {
        return category;
    }

    public VerificationSeverity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getLocation() {
        return location;
    }

    public String getDetails() {
        return details;
    }

    public String getPromptLine() {
        StringBuilder sb = new StringBuilder();
        sb.append("- ").append(severity).append(" ").append(category).append(": ").append(message);
        if (date != null) {
            sb.append(" date=").append(date);
        }
        if (location != null && !location.isBlank()) {
            sb.append(" location=").append(location);
        }
        if (details != null && !details.isBlank()) {
            sb.append(" details=").append(details);
        }
        return sb.toString();
    }
}
