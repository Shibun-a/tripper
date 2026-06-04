package com.embabel.tripper.verification;

import java.util.List;

public record ItineraryStay(
        List<ItineraryDay> days,
        String stayUrl
) {

    public ItineraryStay {
        days = days == null ? List.of() : List.copyOf(days);
    }

    public String locationAndCountry() {
        return days.isEmpty() ? "Unknown location" : days.get(0).locationAndCountry();
    }
}
