package io.github.shibuna.tripsmith.editing;

import io.github.shibuna.tripsmith.verification.PlanVerificationResult;

import java.time.Instant;
import java.util.List;

public record PlanEditVersion(
        int versionNumber,
        Instant createdAt,
        String instruction,
        String planHtml,
        List<EditableItineraryDay> days,
        PlanEditDiff diff,
        PlanVerificationResult verificationResult
) {

    public PlanEditVersion {
        days = List.copyOf(days == null ? List.of() : days);
    }
}
