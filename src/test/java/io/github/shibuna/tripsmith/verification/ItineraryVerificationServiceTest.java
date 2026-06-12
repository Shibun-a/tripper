package io.github.shibuna.tripsmith.verification;

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
        // $750 exceeds even the whole-trip budget (3 days x $200), so it is a real warning.
        ItineraryVerificationRequest request = request(
                "Budget Check",
                "Book a private tour for $750.",
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
        assertTrue(result.getIssues().stream().anyMatch(issue ->
                issue.getCategory() == PlanIssueCategory.BUDGET_EXCEEDED
                        && issue.getSeverity() == VerificationSeverity.WARNING));
        assertFalse(result.getRouteEstimates().isEmpty());
    }

    @Test
    void tiersAmountsWithinTheTripTotalAsInfoNotViolations() {
        // $350 is above the $200 daily budget but inside the $600 trip total: likely a
        // multi-day figure, so it must not warn.
        ItineraryVerificationRequest request = request(
                "Budget Tiering",
                "A two-night spa package costs $350 in total.",
                List.of(
                        new ItineraryDay(LocalDate.of(2026, 7, 1), "Paris,+France"),
                        new ItineraryDay(LocalDate.of(2026, 7, 2), "Dijon,+France"),
                        new ItineraryDay(LocalDate.of(2026, 7, 3), "Beaune,+France")
                ),
                List.of()
        );

        PlanVerificationResult result = service.verifyProposal(request);

        List<VerificationSeverity> budgetSeverities = result.getIssues().stream()
                .filter(issue -> issue.getCategory() == PlanIssueCategory.BUDGET_EXCEEDED)
                .map(PlanVerificationIssue::getSeverity)
                .toList();
        assertEquals(List.of(VerificationSeverity.INFO), budgetSeverities);
        assertFalse(result.isHasErrors());
    }

    @Test
    void rejectsNonPositiveBudgetAsInvalidInput() {
        ItineraryVerificationRequest base = request(
                "Bad Budget",
                "Anything",
                List.of(new ItineraryDay(LocalDate.of(2026, 7, 1), "Paris,+France")),
                List.of()
        );
        ItineraryVerificationRequest request = new ItineraryVerificationRequest(
                base.from(), base.to(), base.transportPreference(),
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1),
                0.0, base.title(), base.planText(), base.days(), base.links(), base.stays()
        );

        PlanVerificationResult result = service.verifyProposal(request);

        assertTrue(hasIssue(result, PlanIssueCategory.INVALID_INPUT));
        assertFalse(hasIssue(result, PlanIssueCategory.BUDGET_EXCEEDED));
    }

    @Test
    void coversAsianDemoRoutesInTheCityCatalog() {
        ItineraryVerificationRequest request = new ItineraryVerificationRequest(
                "Bangkok", "Ho Chi Minh City", "flying",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2),
                200.0, "Asia route", "A short hop.",
                List.of(
                        new ItineraryDay(LocalDate.of(2026, 7, 1), "Bangkok,+Thailand"),
                        new ItineraryDay(LocalDate.of(2026, 7, 2), "Ho Chi Minh City,+Vietnam")
                ),
                List.of(), List.of()
        );

        PlanVerificationResult result = service.verifyProposal(request);

        assertFalse(hasIssue(result, PlanIssueCategory.ROUTE_ESTIMATE_UNAVAILABLE));
        assertEquals(1, result.getRouteEstimates().size());
        assertTrue(result.getRouteEstimates().getFirst().getDistanceKm() > 500);
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
