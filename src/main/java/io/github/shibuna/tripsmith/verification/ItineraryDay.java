package io.github.shibuna.tripsmith.verification;

import java.time.LocalDate;

public record ItineraryDay(
        LocalDate date,
        String locationAndCountry
) {

    public String stayingAt() {
        if (locationAndCountry == null || locationAndCountry.isBlank()) {
            return "Unknown location";
        }
        String[] parts = locationAndCountry.split(",", 2);
        return parts[0].trim();
    }
}
