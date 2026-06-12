package io.github.shibuna.tripsmith.verification;

import java.time.LocalDate;
import java.util.List;

public record ItineraryVerificationRequest(
        String from,
        String to,
        String transportPreference,
        LocalDate departureDate,
        LocalDate returnDate,
        double dailyBudget,
        String title,
        String planText,
        List<ItineraryDay> days,
        List<ItineraryLink> links,
        List<ItineraryStay> stays
) {

    public ItineraryVerificationRequest {
        days = days == null ? List.of() : List.copyOf(days);
        links = links == null ? List.of() : List.copyOf(links);
        stays = stays == null ? List.of() : List.copyOf(stays);
    }

    public ItineraryVerificationRequest withoutStays() {
        return new ItineraryVerificationRequest(
                from,
                to,
                transportPreference,
                departureDate,
                returnDate,
                dailyBudget,
                title,
                planText,
                days,
                links,
                List.of()
        );
    }
}
