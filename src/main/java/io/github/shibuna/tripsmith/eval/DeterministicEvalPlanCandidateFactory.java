package io.github.shibuna.tripsmith.eval;

import io.github.shibuna.tripsmith.verification.ItineraryDay;
import io.github.shibuna.tripsmith.verification.ItineraryLink;
import io.github.shibuna.tripsmith.verification.ItineraryStay;
import io.github.shibuna.tripsmith.verification.ItineraryVerificationRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DeterministicEvalPlanCandidateFactory implements EvalPlanCandidateFactory {

    @Override
    public EvalPlanCandidate create(TravelEvalCase evalCase) {
        List<ItineraryDay> days = plannedDays(evalCase);
        List<ItineraryLink> links = List.of(
                new ItineraryLink("pageLinks", "https://example.com/travel/" + evalCase.id(), "Example planning page"),
                new ItineraryLink("imageLinks", "https://example.com/images/" + evalCase.id() + ".jpg", "Example image"),
                new ItineraryLink("videoLinks", "https://example.com/videos/" + evalCase.id(), "Example video")
        );
        List<ItineraryStay> stays = staysFor(days);

        String planText = planText(evalCase);
        ItineraryVerificationRequest request = new ItineraryVerificationRequest(
                evalCase.from(),
                evalCase.to(),
                evalCase.transportPreference(),
                evalCase.departure(),
                evalCase.returns(),
                evalCase.dailyBudget(),
                evalCase.title(),
                planText,
                days,
                links,
                stays
        );

        int toolCalls = Math.max(3, evalCase.expectedThemes().size() + 2);
        long latencyMs = 900L + (days.size() * 120L) + (evalCase.interests().size() * 40L);
        double tokenCost = 0.004 + (days.size() * 0.0007) + (evalCase.travelers().size() * 0.0004);
        return new EvalPlanCandidate(request, latencyMs, tokenCost, toolCalls, toolCalls);
    }

    private List<ItineraryDay> plannedDays(TravelEvalCase evalCase) {
        List<ItineraryDay> days = new ArrayList<>();
        LocalDate cursor = evalCase.departure();
        long totalDays = evalCase.returns().toEpochDay() - evalCase.departure().toEpochDay() + 1;
        long midpoint = Math.max(1, totalDays / 2);
        int index = 0;
        while (!cursor.isAfter(evalCase.returns())) {
            String location = index < midpoint ? evalCase.from() : evalCase.to();
            if (cursor.equals(evalCase.returns())) {
                location = evalCase.to();
            }
            days.add(new ItineraryDay(cursor, location));
            cursor = cursor.plusDays(1);
            index++;
        }
        return days;
    }

    private List<ItineraryStay> staysFor(List<ItineraryDay> days) {
        List<ItineraryStay> stays = new ArrayList<>();
        List<ItineraryDay> currentDays = new ArrayList<>();
        String currentLocation = null;
        for (ItineraryDay day : days) {
            if (currentLocation == null || currentLocation.equals(day.locationAndCountry())) {
                currentDays.add(day);
                currentLocation = day.locationAndCountry();
                continue;
            }
            stays.add(stay(currentLocation, currentDays));
            currentDays = new ArrayList<>();
            currentDays.add(day);
            currentLocation = day.locationAndCountry();
        }
        if (!currentDays.isEmpty()) {
            stays.add(stay(currentLocation, currentDays));
        }
        return stays;
    }

    private ItineraryStay stay(
            String location,
            List<ItineraryDay> days
    ) {
        String encoded = URLEncoder.encode(location == null ? "Unknown location" : location, StandardCharsets.UTF_8);
        return new ItineraryStay(days, "https://www.airbnb.com/s/" + encoded + "/homes");
    }

    private String planText(TravelEvalCase evalCase) {
        StringBuilder sb = new StringBuilder();
        sb.append(evalCase.title()).append(". ");
        sb.append("Plan a ").append(evalCase.transportPreference()).append(" route from ")
                .append(display(evalCase.from())).append(" to ")
                .append(display(evalCase.to())).append(". ");
        sb.append("Interests: ").append(String.join(", ", evalCase.interests())).append(". ");
        sb.append("Constraints: ").append(String.join(", ", evalCase.constraints())).append(". ");
        sb.append("Keep daily highlights around $")
                .append(Math.max(20, Math.round(evalCase.dailyBudget() * 0.55)))
                .append(" before accommodation. ");
        if (evalCase.requiresKnowledgeCitation()) {
            sb.append("Use user-provided knowledge for local constraints [KB:")
                    .append(evalCase.id())
                    .append("-local]. ");
        }
        return sb.toString();
    }

    private String display(String location) {
        return location == null ? "Unknown location" : location.replace("+", " ");
    }
}
