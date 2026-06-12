package io.github.shibuna.tripsmith.editing;

import io.github.shibuna.tripsmith.verification.ItineraryVerificationService;
import io.github.shibuna.tripsmith.verification.PlanVerificationRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanEditingServiceTest {

    @Test
    void createsEditableSessionWithOriginalVersion() {
        PlanEditingService service = service();

        PlanEditSession session = createSession(service);

        assertEquals("run-1", session.getRunId());
        assertEquals(1, session.getVersions().size());
        assertEquals("PASSED", session.latestVersion().verificationResult().getStatus());
    }

    @Test
    void appliesLocalizedEditAndRecordsDiff() {
        PlanEditingService service = service();
        createSession(service);

        PlanEditSession session = service.applyEdit(
                "run-1",
                "2026-07-11",
                "Avoid long drives and add a rest break."
        );

        PlanEditVersion latest = session.latestVersion();
        assertEquals(2, latest.versionNumber());
        assertEquals(1, latest.diff().changes().size());
        assertEquals(LocalDate.of(2026, 7, 11), latest.diff().changes().getFirst().date());
        assertTrue(latest.days().get(1).editNote().contains("Pacing edit"));
        assertEquals("PASSED", latest.verificationResult().getStatus());
    }

    @Test
    void appliesGlobalBudgetEditAcrossDays() {
        PlanEditingService service = service();
        createSession(service);

        PlanEditSession session = service.applyEdit(
                "run-1",
                "all",
                "Make the plan cheaper with more free activities."
        );

        PlanEditVersion latest = session.latestVersion();
        assertEquals(3, latest.diff().changes().size());
        assertTrue(latest.days().stream().allMatch(day -> day.editNote().contains("Budget edit")));
        assertTrue(latest.planHtml().contains("Edit v2"));
        assertFalse(latest.verificationResult().isHasErrors());
    }

    @Test
    void keepsExistingSessionWhenCompletionPageReloads() {
        PlanEditingService service = service();
        createSession(service);
        service.applyEdit(
                "run-1",
                "all",
                "Make the plan cheaper with more free activities."
        );

        PlanEditSession session = createSession(service);

        assertEquals(2, session.getVersions().size());
        assertEquals("Make the plan cheaper with more free activities.", session.latestVersion().instruction());
    }

    private PlanEditingService service() {
        return new PlanEditingService(
                new PlanEditingRepository(),
                new ItineraryVerificationService(new PlanVerificationRepository())
        );
    }

    private PlanEditSession createSession(PlanEditingService service) {
        return service.createSession(
                "run-1",
                "Paris to Lyon",
                "Paris,+France",
                "Lyon,+France",
                "train",
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 12),
                180.0,
                "Food and history with moderate walking.",
                "<p>Original plan</p>",
                List.of(
                        new EditableItineraryDay(LocalDate.of(2026, 7, 10), "Paris,+France", null),
                        new EditableItineraryDay(LocalDate.of(2026, 7, 11), "Dijon,+France", null),
                        new EditableItineraryDay(LocalDate.of(2026, 7, 12), "Lyon,+France", null)
                )
        );
    }
}
