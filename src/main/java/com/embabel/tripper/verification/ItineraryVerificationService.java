package com.embabel.tripper.verification;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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

    private final PlanVerificationRepository repository;
    private final Map<String, Coordinate> cityCoordinates;

    public ItineraryVerificationService(PlanVerificationRepository repository) {
        this.repository = repository;
        this.cityCoordinates = loadCityCatalog();
    }

    /**
     * City coordinates ship as a classpath resource so route coverage grows by editing data,
     * not code. Estimates stay deterministic and offline — no geocoding at verification time.
     */
    private static Map<String, Coordinate> loadCityCatalog() {
        Map<String, Coordinate> catalog = new HashMap<>();
        ClassPathResource resource = new ClassPathResource("verification/city-coordinates.csv");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmed.split(",");
                if (parts.length != 3) {
                    throw new IllegalStateException("Bad city catalog line: " + line);
                }
                catalog.put(
                        parts[0].trim().toLowerCase(Locale.ROOT),
                        new Coordinate(Double.parseDouble(parts[1].trim()), Double.parseDouble(parts[2].trim()))
                );
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load verifier city catalog", ex);
        }
        return Map.copyOf(catalog);
    }

    public PlanVerificationResult verifyProposal(
            ItineraryVerificationRequest request
    ) {
        return verifyProposal(request, false, 0);
    }

    public PlanVerificationResult verifyProposal(
            ItineraryVerificationRequest request,
            boolean repaired,
            int repairAttempts
    ) {
        VerificationDraft draft = new VerificationDraft();
        validateDateCoverage(request, draft);
        validateLocations(request, draft);
        validateRoute(request, draft);
        validateBudget(request, draft);
        validateLinks(request, draft);
        return persist(draft, repaired, repairAttempts);
    }

    public PlanVerificationResult verifyTravelPlan(
            ItineraryVerificationRequest request,
            boolean repaired,
            int repairAttempts
    ) {
        VerificationDraft draft = new VerificationDraft();
        validateDateCoverage(request, draft);
        validateLocations(request, draft);
        validateRoute(request, draft);
        validateBudget(request, draft);
        validateLinks(request, draft);
        validateStays(request, draft);
        return persist(draft, repaired, repairAttempts);
    }

    public List<PlanVerificationResult> recentResults() {
        return repository.findRecent();
    }

    private void validateDateCoverage(
            ItineraryVerificationRequest request,
            VerificationDraft draft
    ) {
        LocalDate departure = request.departureDate();
        LocalDate returns = request.returnDate();
        if (returns.isBefore(departure)) {
            draft.issue(PlanVerificationIssue.of(
                    PlanIssueCategory.DATE_OUT_OF_RANGE,
                    VerificationSeverity.ERROR,
                    "Return date is before departure date."
            ));
            return;
        }

        Map<LocalDate, Long> dateCounts = new LinkedHashMap<>();
        for (ItineraryDay day : request.days()) {
            if (day.date() == null) {
                draft.issue(PlanVerificationIssue.of(
                        PlanIssueCategory.DATE_GAP,
                        VerificationSeverity.ERROR,
                        "A planned day is missing its date."
                ));
                continue;
            }
            dateCounts.merge(day.date(), 1L, Long::sum);
            if (day.date().isBefore(departure) || day.date().isAfter(returns)) {
                draft.issue(PlanVerificationIssue.onDate(
                        PlanIssueCategory.DATE_OUT_OF_RANGE,
                        VerificationSeverity.ERROR,
                        day.date(),
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
            ItineraryVerificationRequest request,
            VerificationDraft draft
    ) {
        for (ItineraryDay day : request.days()) {
            if (isBlank(day.locationAndCountry())) {
                draft.issue(PlanVerificationIssue.atLocation(
                        PlanIssueCategory.MISSING_LOCATION,
                        VerificationSeverity.ERROR,
                        day.date(),
                        day.locationAndCountry(),
                        "A planned day is missing its locationAndCountry value.",
                        "Expected a Google Maps friendly value such as Dijon,+France."
                ));
            }
        }
    }

    private void validateRoute(
            ItineraryVerificationRequest request,
            VerificationDraft draft
    ) {
        List<ItineraryDay> orderedDays = request.days().stream()
                .filter(day -> day.date() != null)
                .sorted(Comparator.comparing(ItineraryDay::date))
                .toList();

        for (int i = 1; i < orderedDays.size(); i++) {
            ItineraryDay previous = orderedDays.get(i - 1);
            ItineraryDay current = orderedDays.get(i);
            if (sameLocation(previous.locationAndCountry(), current.locationAndCountry())) {
                draft.routeEstimate(new TravelLegEstimate(
                        previous.date(),
                        current.date(),
                        displayLocation(previous.locationAndCountry()),
                        displayLocation(current.locationAndCountry()),
                        0.0,
                        0.0,
                        "same-location",
                        false
                ));
                continue;
            }

            Coordinate from = coordinateFor(previous.locationAndCountry());
            Coordinate to = coordinateFor(current.locationAndCountry());
            if (from == null || to == null) {
                draft.issue(PlanVerificationIssue.atLocation(
                        PlanIssueCategory.ROUTE_ESTIMATE_UNAVAILABLE,
                        VerificationSeverity.INFO,
                        current.date(),
                        current.locationAndCountry(),
                        "Route estimate is unavailable for this leg.",
                        "Add this city to the verifier coordinate catalog or use a maps-backed verifier."
                ));
                continue;
            }

            double distanceKm = haversineKm(from, to) * 1.25;
            double hours = distanceKm / travelSpeedKmh(request.transportPreference());
            boolean tooLong = hours > maxDailyTravelHours(request.transportPreference());
            draft.routeEstimate(new TravelLegEstimate(
                    previous.date(),
                    current.date(),
                    displayLocation(previous.locationAndCountry()),
                    displayLocation(current.locationAndCountry()),
                    distanceKm,
                    hours,
                    "coordinate-haversine",
                    tooLong
            ));
            if (tooLong) {
                draft.issue(PlanVerificationIssue.atLocation(
                        PlanIssueCategory.ROUTE_TOO_LONG,
                        VerificationSeverity.WARNING,
                        current.date(),
                        current.locationAndCountry(),
                        "This itinerary leg may be too long for one travel day.",
                        "Approx " + Math.round(distanceKm) + " km / " + String.format(Locale.ROOT, "%.1f", hours) + " h."
                ));
            }
        }
    }

    private void validateBudget(
            ItineraryVerificationRequest request,
            VerificationDraft draft
    ) {
        if (request.dailyBudget() <= 0.0) {
            draft.issue(PlanVerificationIssue.of(
                    PlanIssueCategory.INVALID_INPUT,
                    VerificationSeverity.ERROR,
                    "Daily budget must be greater than zero."
            ));
            return;
        }

        // A regex cannot tell a per-day price from a trip total, so tier instead of flagging
        // everything: amounts beyond the whole-trip budget are real warnings, amounts between
        // the daily and total budget are likely multi-day figures and only noted as INFO.
        long tripDays = Math.max(1, ChronoUnit.DAYS.between(request.departureDate(), request.returnDate()) + 1);
        double totalBudget = request.dailyBudget() * tripDays;
        Matcher matcher = MONEY_PATTERN.matcher(request.planText() == null ? "" : request.planText());
        while (matcher.find()) {
            double amount = Double.parseDouble(matcher.group(1));
            if (amount > totalBudget) {
                draft.issue(PlanVerificationIssue.of(
                        PlanIssueCategory.BUDGET_EXCEEDED,
                        VerificationSeverity.WARNING,
                        "The plan mentions $" + Math.round(amount)
                                + ", which exceeds the total trip budget of $"
                                + Math.round(totalBudget) + "."
                ));
            } else if (amount > request.dailyBudget()) {
                draft.issue(PlanVerificationIssue.of(
                        PlanIssueCategory.BUDGET_EXCEEDED,
                        VerificationSeverity.INFO,
                        "The plan mentions $" + Math.round(amount)
                                + ", above the daily budget of $" + Math.round(request.dailyBudget())
                                + " but within the trip total; it may be a multi-day figure."
                ));
            }
        }
    }

    private void validateLinks(
            ItineraryVerificationRequest request,
            VerificationDraft draft
    ) {
        for (ItineraryLink link : request.links()) {
            if (!isValidHttpUrl(link.url())) {
                draft.issue(PlanVerificationIssue.of(
                        PlanIssueCategory.INVALID_LINK,
                        VerificationSeverity.ERROR,
                        link.fieldName() + " contains an invalid URL: " + link.url()
                ));
            }
        }
    }

    private void validateStays(
            ItineraryVerificationRequest request,
            VerificationDraft draft
    ) {
        Set<LocalDate> itineraryDates = new HashSet<>();
        for (ItineraryDay day : request.days()) {
            if (day.date() != null) {
                itineraryDates.add(day.date());
            }
        }

        Set<LocalDate> stayDates = new HashSet<>();
        for (ItineraryStay stay : request.stays()) {
            if (stay.days().isEmpty()) {
                draft.issue(PlanVerificationIssue.of(
                        PlanIssueCategory.MISSING_STAY,
                        VerificationSeverity.ERROR,
                        "A stay record contains no covered days."
                ));
            }
            if (isBlank(stay.stayUrl())) {
                draft.issue(PlanVerificationIssue.atLocation(
                        PlanIssueCategory.MISSING_STAY,
                        VerificationSeverity.WARNING,
                        stay.days().isEmpty() ? null : stay.days().get(0).date(),
                        stay.locationAndCountry(),
                        "A stay is missing an Airbnb search URL.",
                        "The final result can still be shown, but accommodation lookup should be retried."
                ));
            } else if (!isValidHttpUrl(stay.stayUrl())) {
                draft.issue(PlanVerificationIssue.atLocation(
                        PlanIssueCategory.INVALID_LINK,
                        VerificationSeverity.ERROR,
                        stay.days().isEmpty() ? null : stay.days().get(0).date(),
                        stay.locationAndCountry(),
                        "A stay has an invalid Airbnb search URL.",
                        stay.stayUrl()
                ));
            }
            for (ItineraryDay day : stay.days()) {
                if (day.date() != null) {
                    stayDates.add(day.date());
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

    private boolean sameLocation(String left, String right) {
        return normalizeCity(left).equals(normalizeCity(right));
    }

    private Coordinate coordinateFor(String locationAndCountry) {
        return cityCoordinates.get(normalizeCity(locationAndCountry));
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
