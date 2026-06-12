package io.github.shibuna.tripsmith.editing;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanEditingRepositoryTest {

    @Test
    void evictsOldestSessionsPastTheCap() {
        PlanEditingRepository repository = new PlanEditingRepository();
        for (int i = 0; i < 110; i++) {
            repository.save(new PlanEditSession(
                    "run-" + i, "title", "A", "B", "driving",
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), 100.0, "constraints"));
        }

        assertTrue(repository.findByRunId("run-0").isEmpty());
        assertTrue(repository.findByRunId("run-9").isEmpty());
        assertTrue(repository.findByRunId("run-109").isPresent());
    }

    @Test
    void resavingASessionRefreshesItsEvictionOrder() {
        PlanEditingRepository repository = new PlanEditingRepository();
        for (int i = 0; i < 100; i++) {
            repository.save(new PlanEditSession(
                    "run-" + i, "title", "A", "B", "driving",
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), 100.0, "constraints"));
        }

        // Touch the oldest session, then push one more: the untouched run-1 should fall out first.
        repository.save(repository.findByRunId("run-0").orElseThrow());
        repository.save(new PlanEditSession(
                "run-100", "title", "A", "B", "driving",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), 100.0, "constraints"));

        assertTrue(repository.findByRunId("run-0").isPresent());
        assertTrue(repository.findByRunId("run-1").isEmpty());
    }
}
