package io.github.shibuna.tripsmith.editing;

import io.github.shibuna.tripsmith.verification.ItineraryDay;
import io.github.shibuna.tripsmith.verification.ItineraryVerificationRequest;
import io.github.shibuna.tripsmith.verification.ItineraryVerificationService;
import io.github.shibuna.tripsmith.verification.PlanVerificationResult;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class PlanEditingService {

    private final PlanEditSessionStore repository;
    private final ItineraryVerificationService verificationService;

    public PlanEditingService(
            PlanEditSessionStore repository,
            ItineraryVerificationService verificationService
    ) {
        this.repository = repository;
        this.verificationService = verificationService;
    }

    public PlanEditSession createSession(
            String runId,
            String title,
            String fromLocation,
            String toLocation,
            String transportPreference,
            LocalDate departureDate,
            LocalDate returnDate,
            double dailyBudget,
            String constraintsSummary,
            String planHtml,
            List<EditableItineraryDay> days
    ) {
        Optional<PlanEditSession> existingSession = repository.findByRunId(runId);
        if (existingSession.isPresent()) {
            return existingSession.get();
        }

        PlanEditSession session = new PlanEditSession(
                runId,
                title,
                fromLocation,
                toLocation,
                transportPreference,
                departureDate,
                returnDate,
                dailyBudget,
                constraintsSummary
        );
        PlanVerificationResult verification = verify(session, planHtml, days);
        session.addVersion(new PlanEditVersion(
                1,
                Instant.now(),
                "Original generated plan",
                planHtml,
                days,
                new PlanEditDiff("Original version.", List.of()),
                verification
        ));
        return repository.save(session);
    }

    public Optional<PlanEditSession> findSession(String runId) {
        return repository.findByRunId(runId);
    }

    public PlanEditSession applyEdit(
            String runId,
            String selectedDate,
            String instruction
    ) {
        PlanEditSession session = repository.findByRunId(runId)
                .orElseThrow(() -> new IllegalArgumentException("Plan edit session not found: " + runId));
        PlanEditVersion latest = session.latestVersion();
        String normalizedInstruction = normalizeInstruction(instruction);
        LocalDate targetDate = parseDate(selectedDate);

        List<EditableItineraryDay> editedDays = new ArrayList<>();
        List<PlanEditChange> changes = new ArrayList<>();
        String note = editNoteFor(normalizedInstruction);
        for (EditableItineraryDay day : latest.days()) {
            boolean shouldEdit = targetDate == null || targetDate.equals(day.date());
            if (shouldEdit) {
                String before = day.editNote() == null ? "" : day.editNote();
                String after = appendNote(before, note);
                editedDays.add(day.withEditNote(after));
                changes.add(new PlanEditChange(day.date(), before.isBlank() ? "No edit note" : before, after));
            } else {
                editedDays.add(day);
            }
        }

        if (changes.isEmpty()) {
            editedDays = latest.days();
            changes.add(new PlanEditChange(null, "No matching day", "Added global instruction only"));
        }

        String editedHtml = latest.planHtml() + editHtml(latest.versionNumber() + 1, targetDate, normalizedInstruction);
        PlanVerificationResult verification = verify(session, editedHtml, editedDays);
        PlanEditVersion version = new PlanEditVersion(
                latest.versionNumber() + 1,
                Instant.now(),
                normalizedInstruction,
                editedHtml,
                editedDays,
                new PlanEditDiff(diffSummary(targetDate, changes.size()), changes),
                verification
        );
        session.addVersion(version);
        return repository.save(session);
    }

    private PlanVerificationResult verify(
            PlanEditSession session,
            String planHtml,
            List<EditableItineraryDay> days
    ) {
        ItineraryVerificationRequest request = new ItineraryVerificationRequest(
                session.getFromLocation(),
                session.getToLocation(),
                session.getTransportPreference(),
                session.getDepartureDate(),
                session.getReturnDate(),
                session.getDailyBudget(),
                session.getTitle(),
                planHtml,
                days.stream()
                        .map(day -> new ItineraryDay(day.date(), day.locationAndCountry()))
                        .toList(),
                List.of(),
                List.of()
        );
        return verificationService.verifyProposal(request);
    }

    private String normalizeInstruction(String instruction) {
        if (instruction == null || instruction.isBlank()) {
            throw new IllegalArgumentException("Edit instruction must not be blank");
        }
        return instruction.replaceAll("\\s+", " ").trim();
    }

    private LocalDate parseDate(String selectedDate) {
        if (selectedDate == null || selectedDate.isBlank() || "all".equalsIgnoreCase(selectedDate)) {
            return null;
        }
        return LocalDate.parse(selectedDate);
    }

    private String editNoteFor(String instruction) {
        String lower = instruction.toLowerCase(Locale.ROOT);
        if (lower.contains("cheap") || lower.contains("budget") || lower.contains("cheaper")) {
            return "Budget edit: prefer lower-cost meals, transit, and free or low-cost activities. Instruction: " + instruction;
        }
        if (lower.contains("drive") || lower.contains("transfer") || lower.contains("rush") || lower.contains("rest")) {
            return "Pacing edit: reduce rushed transfers and add recovery time. Instruction: " + instruction;
        }
        if (lower.contains("nature") || lower.contains("park") || lower.contains("walk")) {
            return "Theme edit: add more outdoor and nature-oriented time. Instruction: " + instruction;
        }
        if (lower.contains("food") || lower.contains("wine") || lower.contains("restaurant")) {
            return "Theme edit: add more food and drink recommendations. Instruction: " + instruction;
        }
        return "User edit: " + instruction;
    }

    private String appendNote(
            String before,
            String note
    ) {
        if (before == null || before.isBlank()) {
            return note;
        }
        return before + " " + note;
    }

    private String editHtml(
            int versionNumber,
            LocalDate targetDate,
            String instruction
    ) {
        return """

                <h4>Edit v%s</h4>
                <p><strong>Scope:</strong> %s</p>
                <p><strong>Instruction:</strong> %s</p>
                """.formatted(
                versionNumber,
                targetDate == null ? "All days" : HtmlUtils.htmlEscape(targetDate.toString()),
                HtmlUtils.htmlEscape(instruction)
        );
    }

    private String diffSummary(
            LocalDate targetDate,
            int changeCount
    ) {
        if (targetDate == null) {
            return "Applied edit across " + changeCount + " day(s).";
        }
        return "Applied edit to " + targetDate + ".";
    }
}
