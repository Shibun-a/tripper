package io.github.shibuna.tripsmith.editing;

import java.time.LocalDate;

public record PlanEditChange(
        LocalDate date,
        String beforeValue,
        String afterValue
) {
}
