package com.embabel.tripper.verification;

import com.embabel.agent.domain.library.InternetResource;
import com.embabel.tripper.agent.Day;
import com.embabel.tripper.agent.JourneyTravelBrief;
import com.embabel.tripper.agent.ProposedTravelPlan;
import com.embabel.tripper.agent.Stay;
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
        ProposedTravelPlan proposal = proposal(
                "Simple Burgundy",
                "A compact route.",
                List.of(
                        new Day(LocalDate.of(2026, 7, 1), "Paris,+France"),
                        new Day(LocalDate.of(2026, 7, 3), "")
                ),
                List.of()
        );

        PlanVerificationResult result = service.verifyProposal(brief(), proposal);

        assertTrue(result.isHasErrors());
        assertTrue(hasIssue(result, PlanIssueCategory.DATE_GAP));
        assertTrue(hasIssue(result, PlanIssueCategory.MISSING_LOCATION));
    }

    @Test
    void flagsInvalidLinksAndOverBudgetAmounts() {
        ProposedTravelPlan proposal = proposal(
                "Budget Check",
                "Book a tasting menu for $350.",
                List.of(
                        new Day(LocalDate.of(2026, 7, 1), "Paris,+France"),
                        new Day(LocalDate.of(2026, 7, 2), "Dijon,+France"),
                        new Day(LocalDate.of(2026, 7, 3), "Beaune,+France")
                ),
                List.of(new InternetResource("not a url", "broken link"))
        );

        PlanVerificationResult result = service.verifyProposal(brief(), proposal);

        assertTrue(result.isHasErrors());
        assertTrue(hasIssue(result, PlanIssueCategory.INVALID_LINK));
        assertTrue(hasIssue(result, PlanIssueCategory.BUDGET_EXCEEDED));
        assertFalse(result.getRouteEstimates().isEmpty());
    }

    @Test
    void checksStayCoverageAgainstPlannedDays() {
        ProposedTravelPlan proposal = proposal(
                "Stay Check",
                "Use simple hotels.",
                List.of(
                        new Day(LocalDate.of(2026, 7, 1), "Paris,+France"),
                        new Day(LocalDate.of(2026, 7, 2), "Dijon,+France"),
                        new Day(LocalDate.of(2026, 7, 3), "Beaune,+France")
                ),
                List.of()
        );

        Stay partialStay = new Stay(
                List.of(new Day(LocalDate.of(2026, 7, 1), "Paris,+France")),
                "https://www.airbnb.com/s/Paris--France/homes"
        );

        PlanVerificationResult result = service.verifyTravelPlan(
                brief(),
                proposal,
                List.of(partialStay),
                false,
                0
        );

        assertTrue(result.isHasErrors());
        assertEquals(2, result.getIssues().stream()
                .filter(issue -> issue.getCategory() == PlanIssueCategory.MISSING_STAY)
                .count());
    }

    private JourneyTravelBrief brief() {
        return new JourneyTravelBrief(
                "Paris",
                "Beaune",
                "driving",
                "A short food and wine road trip.",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 3),
                200.0
        );
    }

    private ProposedTravelPlan proposal(
            String title,
            String plan,
            List<Day> days,
            List<InternetResource> pageLinks
    ) {
        return new ProposedTravelPlan(
                title,
                plan,
                days,
                List.of(),
                List.of(),
                pageLinks,
                List.of("France")
        );
    }

    private boolean hasIssue(
            PlanVerificationResult result,
            PlanIssueCategory category
    ) {
        return result.getIssues().stream().anyMatch(issue -> issue.getCategory() == category);
    }
}
