package com.embabel.tripper.verification;

import java.time.LocalDate;

public final class TravelLegEstimate {

    private final LocalDate fromDate;
    private final LocalDate toDate;
    private final String fromLocation;
    private final String toLocation;
    private final Double distanceKm;
    private final Double estimatedHours;
    private final String method;
    private final boolean routeTooLong;

    public TravelLegEstimate(
            LocalDate fromDate,
            LocalDate toDate,
            String fromLocation,
            String toLocation,
            Double distanceKm,
            Double estimatedHours,
            String method,
            boolean routeTooLong
    ) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.fromLocation = fromLocation;
        this.toLocation = toLocation;
        this.distanceKm = distanceKm;
        this.estimatedHours = estimatedHours;
        this.method = method;
        this.routeTooLong = routeTooLong;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public String getFromLocation() {
        return fromLocation;
    }

    public String getToLocation() {
        return toLocation;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public Double getEstimatedHours() {
        return estimatedHours;
    }

    public String getMethod() {
        return method;
    }

    public boolean isRouteTooLong() {
        return routeTooLong;
    }
}
