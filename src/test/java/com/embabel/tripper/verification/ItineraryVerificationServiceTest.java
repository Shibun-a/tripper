package com.embabel.tripper.verification;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItineraryVerificationServiceTest {

    private final ItineraryVerificationService service = new ItineraryVerificationService(
            new PlanVerificationRepository()
    );

    @Test
    void detectsDateGapsAndMissingLocations() {
        ItineraryVerificationRequest request = request(
                "Simple Burgundy",
                "A compact route.",
                List.of(
                        new ItineraryDay(LocalDate.of(2026, 7, 1), "Paris,+France"),
                        new ItineraryDay(LocalDate.of(2026, 7, 3), "")
                ),
                List.of()
        );

        PlanVerificationResult result = service.verifyProposal(request);

        assertTrue(result.isHasErrors());
        assertTrue(hasIssue(result, PlanIssueCategory.DATE_GAP));
        assertTrue(hasIssue(result, PlanIssueCategory.MISSING_LOCATION));
    }

    @Test
    void flagsInvalidLinksAndOverBudgetAmounts() {
        ItineraryVerificationRequest request = request(
                "Budget Check",
                "Book a tasting menu for $350.",
                List.of(
                        new ItineraryDay(LocalDate.of(2026, 7, 1), "Paris,+France"),
                        new ItineraryDay(LocalDate.of(2026, 7, 2), "Dijon,+France"),
                        new ItineraryDay(LocalDate.of(2026, 7, 3), "Beaune,+France")
                ),
                List.of(new ItineraryLink("pageLinks", "not a url", "broken link"))
        );

        PlanVerificationResult result = service.verifyProposal(request);

        assertTrue(result.isHasErrors());
        assertTrue(hasIssue(result, PlanIssueCategory.INVALID_LINK));
        assertTrue(hasIssue(result, PlanIssueCategory.BUDGET_EXCEEDED));
        assertFalse(result.getRouteEstimates().isEmpty());
    }

    @Test
    void checksStayCoverageAgainstPlannedDays() {
        ItineraryVerificationRequest request = request(
                "Stay Check",
                "Use simple hotels.",
                List.of(
                        new ItineraryDay(LocalDate.of(2026, 7, 1), "Paris,+France"),
                        new ItineraryDay(LocalDate.of(2026, 7, 2), "Dijon,+France"),
                        new ItineraryDay(LocalDate.of(2026, 7, 3), "Beaune,+France")
                ),
                List.of()
        );

        ItineraryStay partialStay = new ItineraryStay(
                List.of(new ItineraryDay(LocalDate.of(2026, 7, 1), "Paris,+France")),
                "https://www.airbnb.com/s/Paris--France/homes"
        );

        PlanVerificationResult result = service.verifyTravelPlan(
                withStays(request, List.of(partialStay)),
                false,
                0
        );

        assertTrue(result.isHasErrors());
        assertEquals(2, result.getIssues().stream()
                .filter(issue -> issue.getCategory() == PlanIssueCategory.MISSING_STAY)
                .count());
    }

    private ItineraryVerificationRequest request(
            String title,
            String plan,
            List<ItineraryDay> days,
            List<ItineraryLink> links
    ) {
        return new ItineraryVerificationRequest(
                "Paris",
                "Beaune",
                "driving",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 3),
                200.0,
                title,
                plan,
                days,
                links,
                List.of()
        );
    }

    private ItineraryVerificationRequest withStays(
            ItineraryVerificationRequest request,
            List<ItineraryStay> stays
    ) {
        return new ItineraryVerificationRequest(
                request.from(),
                request.to(),
                request.transportPreference(),
                request.departureDate(),
                request.returnDate(),
                request.dailyBudget(),
                request.title(),
                request.planText(),
                request.days(),
                request.links(),
                stays
        );
    }

    private boolean hasIssue(
            PlanVerificationResult result,
            PlanIssueCategory category
    ) {
        return result.getIssues().stream().anyMatch(issue -> issue.getCategory() == category);
    }
}
