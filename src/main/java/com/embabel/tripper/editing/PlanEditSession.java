package com.embabel.tripper.editing;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class PlanEditSession {

    private final String runId;
    private final String title;
    private final String fromLocation;
    private final String toLocation;
    private final String transportPreference;
    private final LocalDate departureDate;
    private final LocalDate returnDate;
    private final double dailyBudget;
    private final String constraintsSummary;
    private final Instant createdAt;
    private final List<PlanEditVersion> versions = new ArrayList<>();

    public PlanEditSession(
            String runId,
            String title,
            String fromLocation,
            String toLocation,
            String transportPreference,
            LocalDate departureDate,
            LocalDate returnDate,
            double dailyBudget,
            String constraintsSummary
    ) {
        this(runId, title, fromLocation, toLocation, transportPreference,
                departureDate, returnDate, dailyBudget, constraintsSummary, Instant.now());
    }

    /** Full-state restore used by persistence adapters when rehydrating a stored session. */
    PlanEditSession(
            String runId,
            String title,
            String fromLocation,
            String toLocation,
            String transportPreference,
            LocalDate departureDate,
            LocalDate returnDate,
            double dailyBudget,
            String constraintsSummary,
            Instant createdAt
    ) {
        this.runId = runId;
        this.title = title;
        this.fromLocation = fromLocation;
        this.toLocation = toLocation;
        this.transportPreference = transportPreference;
        this.departureDate = departureDate;
        this.returnDate = returnDate;
        this.dailyBudget = dailyBudget;
        this.constraintsSummary = constraintsSummary;
        this.createdAt = createdAt;
    }

    public void addVersion(PlanEditVersion version) {
        versions.add(version);
    }

    public PlanEditVersion latestVersion() {
        if (versions.isEmpty()) {
            return null;
        }
        return versions.getLast();
    }

    public String getRunId() {
        return runId;
    }

    public String getTitle() {
        return title;
    }

    public String getFromLocation() {
        return fromLocation;
    }

    public String getToLocation() {
        return toLocation;
    }

    public String getTransportPreference() {
        return transportPreference;
    }

    public LocalDate getDepartureDate() {
        return departureDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public double getDailyBudget() {
        return dailyBudget;
    }

    public String getConstraintsSummary() {
        return constraintsSummary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<PlanEditVersion> getVersions() {
        return List.copyOf(versions);
    }
}
