package com.embabel.tripper.eval;

import java.time.LocalDate;
import java.util.List;

public record TravelEvalCase(
        String id,
        String title,
        String from,
        String to,
        String transportPreference,
        String departureDate,
        String returnDate,
        double dailyBudget,
        List<String> travelers,
        List<String> constraints,
        List<String> interests,
        boolean requiresKnowledgeCitation,
        List<String> expectedCountries,
        List<String> expectedThemes
) {

    public TravelEvalCase {
        travelers = travelers == null ? List.of() : List.copyOf(travelers);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        interests = interests == null ? List.of() : List.copyOf(interests);
        expectedCountries = expectedCountries == null ? List.of() : List.copyOf(expectedCountries);
        expectedThemes = expectedThemes == null ? List.of() : List.copyOf(expectedThemes);
    }

    public LocalDate departure() {
        return LocalDate.parse(departureDate);
    }

    public LocalDate returns() {
        return LocalDate.parse(returnDate);
    }
}
