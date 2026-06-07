package com.embabel.tripper.editing;

import java.time.LocalDate;

public record EditableItineraryDay(
        LocalDate date,
        String locationAndCountry,
        String editNote
) {

    public EditableItineraryDay withEditNote(String note) {
        return new EditableItineraryDay(date, locationAndCountry, note);
    }

    public String stayingAt() {
        if (locationAndCountry == null || locationAndCountry.isBlank()) {
            return "Unknown location";
        }
        return locationAndCountry.split(",", 2)[0].replace("+", " ").trim();
    }
}
