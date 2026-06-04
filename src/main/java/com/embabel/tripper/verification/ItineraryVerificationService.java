package com.embabel.tripper.verification;

import com.embabel.agent.domain.library.InternetResource;
import com.embabel.tripper.agent.Day;
import com.embabel.tripper.agent.JourneyTravelBrief;
import com.embabel.tripper.agent.ProposedTravelPlan;
import com.embabel.tripper.agent.Stay;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ItineraryVerificationService {

    private static final Pattern MONEY_PATTERN = Pattern.compile(
            "(?i)(?:\\$|usd\\s*)(\\d{2,5})(?:\\.\\d{1,2})?"
    );

    private static final Map<String, Coordinate> CITY_COORDINATES = Map.ofEntries(
            Map.entry("barcelona", new Coordinate(41.3874, 2.1686)),
            Map.entry("bordeaux", new Coordinate(44.8378, -0.5792)),
            Map.entry("paris", new Coordinate(48.8566, 2.3522)),
            Map.entry("dijon", new Coordinate(47.3220, 5.0415)),
            Map.entry("beaune", new Coordinate(47.0260, 4.8400)),
            Map.entry("lyon", new Coordinate(45.7640, 4.8357)),
            Map.entry("marseille", new Coordinate(43.2965, 5.3698)),
            Map.entry("nice", new Coordinate(43.7102, 7.2620)),
            Map.entry("madrid", new Coordinate(40.4168, -3.7038)),
            Map.entry("rome", new Coordinate(41.9028, 12.4964)),
            Map.entry("florence", new Coordinate(43.7696, 11.2558)),
            Map.entry("venice", new Coordinate(45.4408, 12.3155)),
            Map.entry("london", new Coordinate(51.5072, -0.1276)),
            Map.entry("amsterdam", new Coordinate(52.3676, 4.9041)),
            Map.entry("brussels", new Coordinate(50.8503, 4.3517)),
            Map.entry("berlin", new Coordinate(52.5200, 13.4050)),
            Map.entry("munich", new Coordinate(48.1351, 11.5820)),
            Map.entry("vienna", new Coordinate(48.2082, 16.3738)),
            Map.entry("prague", new Coordinate(50.0755, 14.4378)),
            Map.entry("lisbon", new Coordinate(38.7223, -9.1393)),
            Map.entry("porto", new Coordinate(41.1579, -8.6291)),
            Map.entry("new york", new Coordinate(40.7128, -74.0060)),
            Map.entry("san francisco", new Coordinate(37.7749, -122.4194)),
            Map.entry("los angeles", new Coordinate(34.0522, -118.2437)),
            Map.entry("tokyo", new Coordinate(35.6762, 139.6503)),
            Map.entry("kyoto", new Coordinate(35.0116, 135.7681)),
            Map.entry("osaka", new Coordinate(34.6937, 135.5023)),
            Map.entry("shanghai", new Coordinate(31.2304, 121.4737)),
            Map.entry("beijing", new Coordinate(39.9042, 116.4074)),
            Map.entry("singapore", new Coordinate(1.3521, 103.8198))
    );

    private final PlanVerificationRepository repository;

    public ItineraryVerificationService(PlanVerificationRepository repository) {
        this.repository = repository;
    }

    public PlanVerificationResult verifyProposal(
            JourneyTravelBrief brief,
            ProposedTravelPlan proposal
    ) {
        return verifyProposal(brief, proposal, false, 0);
    }

    public PlanVerificationResult verifyProposal(
            JourneyTravelBrief brief,
            ProposedTravelPlan proposal,
            boolean repaired,
            int repairAttempts
    ) {
        VerificationDraft draft = new VerificationDraft();
        validateDateCoverage(brief, proposal, draft);
        validateLocations(proposal, draft);
        validateRoute(brief, proposal, draft);
        validateBudget(brief, proposal, draft);
        validateLinks(proposal, draft);
        return persist(draft, repaired, repairAttempts);
    }

    public PlanVerificationResult verifyTravelPlan(
            JourneyTravelBrief brief,
            ProposedTravelPlan proposal,
            List<Stay> stays,
            boolean repaired,
            int repairAttempts
    ) {
        VerificationDraft draft = new VerificationDraft();
        validateDateCoverage(brief, proposal, draft);
        validateLocations(proposal, draft);
        validateRoute(brief, proposal, draft);
        validateBudget(brief, proposal, draft);
        validateLinks(proposal, draft);
        validateStays(proposal, stays, draft);
        return persist(draft, repaired, repairAttempts);
    }

    public List<PlanVerificationResult> recentResults() {
        return repository.findRecent();
    }

    private void validateDateCoverage(
            JourneyTravelBrief brief,
            ProposedTravelPlan proposal,
            VerificationDraft draft
    ) {
        LocalDate departure = brief.getDepartureDate();
        LocalDate returns = brief.getReturnDate();
        if (returns.isBefore(departure)) {
            draft.issue(PlanVerificationIssue.of(
                    PlanIssueCategory.DATE_OUT_OF_RANGE,
                    VerificationSeverity.ERROR,
                    "Return date is before departure date."
            ));
            return;
        }

        Map<LocalDate, Long> dateCounts = new LinkedHashMap<>();
        for (Day day : daysOf(proposal)) {
            if (day.getDate() == null) {
                draft.issue(PlanVerificationIssue.of(
                        PlanIssueCategory.DATE_GAP,
                        VerificationSeverity.ERROR,
                        "A planned day is missing its date."
                ));
                continue;
            }
            dateCounts.merge(day.getDate(), 1L, Long::sum);
            if (day.getDate().isBefore(departure) || day.getDate().isAfter(returns)) {
                draft.issue(PlanVerificationIssue.onDate(
                        PlanIssueCategory.DATE_OUT_OF_RANGE,
                        VerificationSeverity.ERROR,
                        day.getDate(),
                        "The itinerary includes a day outside the requested travel window."
                ));
            }
        }

        LocalDate cursor = departure;
        while (!cursor.isAfter(returns)) {
            if (!dateCounts.containsKey(cursor)) {
                draft.issue(PlanVerificationIssue.onDate(
                        PlanIssueCategory.DATE_GAP,
                        VerificationSeverity.ERROR,
                        cursor,
                        "The itinerary does not cover this requested travel date."
                ));
            }
            cursor = cursor.plusDays(1);
        }

        dateCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .forEach(entry -> draft.issue(PlanVerificationIssue.onDate(
                        PlanIssueCategory.DUPLICATE_DATE,
                        VerificationSeverity.WARNING,
                        entry.getKey(),
                        "The itinerary contains multiple entries for the same date."
                )));
    }

    private void validateLocations(
            ProposedTravelPlan proposal,
            VerificationDraft draft
    ) {
        for (Day day : daysOf(proposal)) {
            if (isBlank(day.getLocationAndCountry())) {
                draft.issue(PlanVerificationIssue.atLocation(
                        PlanIssueCategory.MISSING_LOCATION,
                        VerificationSeverity.ERROR,
                        day.getDate(),
                        day.getLocationAndCountry(),
                        "A planned day is missing its locationAndCountry value.",
                        "Expected a Google Maps friendly value such as Dijon,+France."
                ));
            }
        }
    }

    private void validateRoute(
            JourneyTravelBrief brief,
            ProposedTravelPlan proposal,
            VerificationDraft draft
    ) {
        List<Day> orderedDays = daysOf(proposal).stream()
                .filter(day -> day.getDate() != null)
                .sorted(Comparator.comparing(Day::getDate))
                .toList();

        for (int i = 1; i < orderedDays.size(); i++) {
            Day previous = orderedDays.get(i - 1);
            Day current = orderedDays.get(i);
            if (sameLocation(previous.getLocationAndCountry(), current.getLocationAndCountry())) {
                draft.routeEstimate(new TravelLegEstimate(
                        previous.getDate(),
                        current.getDate(),
                        displayLocation(previous.getLocationAndCountry()),
                        displayLocation(current.getLocationAndCountry()),
                        0.0,
                        0.0,
                        "same-location",
                        false
                ));
                continue;
            }

            Coordinate from = coordinateFor(previous.getLocationAndCountry());
            Coordinate to = coordinateFor(current.getLocationAndCountry());
            if (from == null || to == null) {
                draft.issue(PlanVerificationIssue.atLocation(
                        PlanIssueCategory.ROUTE_ESTIMATE_UNAVAILABLE,
                        VerificationSeverity.INFO,
                        current.getDate(),
                        current.getLocationAndCountry(),
                        "Route estimate is unavailable for this leg.",
                        "Add this city to the verifier coordinate catalog or use a maps-backed verifier."
                ));
                continue;
            }

            double distanceKm = haversineKm(from, to) * 1.25;
            double hours = distanceKm / travelSpeedKmh(brief.getTransportPreference());
            boolean tooLong = hours > maxDailyTravelHours(brief.getTransportPreference());
            draft.routeEstimate(new TravelLegEstimate(
                    previous.getDate(),
                    current.getDate(),
                    displayLocation(previous.getLocationAndCountry()),
                    displayLocation(current.getLocationAndCountry()),
                    distanceKm,
                    hours,
                    "coordinate-haversine",
                    tooLong
            ));
            if (tooLong) {
                draft.issue(PlanVerificationIssue.atLocation(
                        PlanIssueCategory.ROUTE_TOO_LONG,
                        VerificationSeverity.WARNING,
                        current.getDate(),
                        current.getLocationAndCountry(),
                        "This itinerary leg may be too long for one travel day.",
                        "Approx " + Math.round(distanceKm) + " km / " + String.format(Locale.ROOT, "%.1f", hours) + " h."
                ));
            }
        }
    }

    private void validateBudget(
            JourneyTravelBrief brief,
            ProposedTravelPlan proposal,
            VerificationDraft draft
    ) {
        if (brief.getDailyBudget() <= 0.0) {
            draft.issue(PlanVerificationIssue.of(
                    PlanIssueCategory.BUDGET_EXCEEDED,
                    VerificationSeverity.ERROR,
                    "Daily budget must be greater than zero."
            ));
            return;
        }

        Matcher matcher = MONEY_PATTERN.matcher(proposal.getPlan() == null ? "" : proposal.getPlan());
        while (matcher.find()) {
            double amount = Double.parseDouble(matcher.group(1));
            if (amount > brief.getDailyBudget()) {
                draft.issue(PlanVerificationIssue.of(
                        PlanIssueCategory.BUDGET_EXCEEDED,
                        VerificationSeverity.WARNING,
                        "The plan mentions $" + Math.round(amount)
                                + ", which exceeds the requested daily budget of $"
                                + Math.round(brief.getDailyBudget()) + "."
                ));
            }
        }
    }

    private void validateLinks(
            ProposedTravelPlan proposal,
            VerificationDraft draft
    ) {
        validateResources("pageLinks", proposal.getPageLinks(), draft);
        validateResources("imageLinks", proposal.getImageLinks(), draft);
        validateResources("videoLinks", proposal.getVideoLinks(), draft);
    }

    private void validateResources(
            String fieldName,
            List<InternetResource> resources,
            VerificationDraft draft
    ) {
        if (resources == null) {
            return;
        }
        for (InternetResource resource : resources) {
            String url = resource.getUrl();
            if (!isValidHttpUrl(url)) {
                draft.issue(PlanVerificationIssue.of(
                        PlanIssueCategory.INVALID_LINK,
                        VerificationSeverity.ERROR,
                        fieldName + " contains an invalid URL: " + url
                ));
            }
        }
    }

    private void validateStays(
            ProposedTravelPlan proposal,
            List<Stay> stays,
            VerificationDraft draft
    ) {
        Set<LocalDate> itineraryDates = new HashSet<>();
        for (Day day : daysOf(proposal)) {
            if (day.getDate() != null) {
                itineraryDates.add(day.getDate());
            }
        }

        Set<LocalDate> stayDates = new HashSet<>();
        for (Stay stay : stays) {
            if (stay.getDays().isEmpty()) {
                draft.issue(PlanVerificationIssue.of(
                        PlanIssueCategory.MISSING_STAY,
                        VerificationSeverity.ERROR,
                        "A stay record contains no covered days."
                ));
            }
            if (isBlank(stay.getAirbnbUrl())) {
                draft.issue(PlanVerificationIssue.atLocation(
                        PlanIssueCategory.MISSING_STAY,
                        VerificationSeverity.WARNING,
                        stay.getDays().isEmpty() ? null : stay.getDays().getFirst().getDate(),
                        stay.locationAndCountry(),
                        "A stay is missing an Airbnb search URL.",
                        "The final result can still be shown, but accommodation lookup should be retried."
                ));
            } else if (!isValidHttpUrl(stay.getAirbnbUrl())) {
                draft.issue(PlanVerificationIssue.atLocation(
                        PlanIssueCategory.INVALID_LINK,
                        VerificationSeverity.ERROR,
                        stay.getDays().isEmpty() ? null : stay.getDays().getFirst().getDate(),
                        stay.locationAndCountry(),
                        "A stay has an invalid Airbnb search URL.",
                        stay.getAirbnbUrl()
                ));
            }
            for (Day day : stay.getDays()) {
                if (day.getDate() != null) {
                    stayDates.add(day.getDate());
                }
            }
        }

        for (LocalDate date : itineraryDates) {
            if (!stayDates.contains(date)) {
                draft.issue(PlanVerificationIssue.onDate(
                        PlanIssueCategory.MISSING_STAY,
                        VerificationSeverity.ERROR,
                        date,
                        "No stay covers this planned travel date."
                ));
            }
        }
    }

    private PlanVerificationResult persist(
            VerificationDraft draft,
            boolean repaired,
            int repairAttempts
    ) {
        return repository.save(new PlanVerificationResult(
                UUID.randomUUID().toString(),
                Instant.now(),
                draft.issues(),
                draft.routeEstimates(),
                repaired,
                repairAttempts
        ));
    }

    private boolean isValidHttpUrl(String url) {
        if (isBlank(url)) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private List<Day> daysOf(ProposedTravelPlan proposal) {
        if (proposal.getDays() == null) {
            return List.of();
        }
        return proposal.getDays();
    }

    private boolean sameLocation(String left, String right) {
        return normalizeCity(left).equals(normalizeCity(right));
    }

    private Coordinate coordinateFor(String locationAndCountry) {
        return CITY_COORDINATES.get(normalizeCity(locationAndCountry));
    }

    private String normalizeCity(String locationAndCountry) {
        if (locationAndCountry == null) {
            return "";
        }
        String city = locationAndCountry.split(",", 2)[0].replace("+", " ").trim();
        return city.toLowerCase(Locale.ROOT);
    }

    private String displayLocation(String locationAndCountry) {
        if (isBlank(locationAndCountry)) {
            return "Unknown location";
        }
        return locationAndCountry.replace("+", " ");
    }

    private double travelSpeedKmh(String preference) {
        String normalized = preference == null ? "" : preference.toLowerCase(Locale.ROOT);
        if (normalized.contains("train")) {
            return 95.0;
        }
        if (normalized.contains("flight") || normalized.contains("plane")) {
            return 650.0;
        }
        if (normalized.contains("walk")) {
            return 5.0;
        }
        return 70.0;
    }

    private double maxDailyTravelHours(String preference) {
        String normalized = preference == null ? "" : preference.toLowerCase(Locale.ROOT);
        if (normalized.contains("flight") || normalized.contains("plane")) {
            return 7.0;
        }
        if (normalized.contains("walk")) {
            return 6.0;
        }
        return 6.5;
    }

    private double haversineKm(Coordinate from, Coordinate to) {
        double earthRadiusKm = 6371.0;
        double latDistance = Math.toRadians(to.latitude() - from.latitude());
        double lonDistance = Math.toRadians(to.longitude() - from.longitude());
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(from.latitude()))
                * Math.cos(Math.toRadians(to.latitude()))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Coordinate(double latitude, double longitude) {
    }

    private static final class VerificationDraft {

        private final List<PlanVerificationIssue> issues = new ArrayList<>();
        private final List<TravelLegEstimate> routeEstimates = new ArrayList<>();

        void issue(PlanVerificationIssue issue) {
            issues.add(issue);
        }

        void routeEstimate(TravelLegEstimate routeEstimate) {
            routeEstimates.add(routeEstimate);
        }

        List<PlanVerificationIssue> issues() {
            return issues;
        }

        List<TravelLegEstimate> routeEstimates() {
            return routeEstimates;
        }
    }
}
