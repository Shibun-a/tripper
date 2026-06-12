package io.github.shibuna.tripsmith.editing;

import java.util.List;

public record PlanEditDiff(
        String summary,
        List<PlanEditChange> changes
) {

    public PlanEditDiff {
        changes = List.copyOf(changes == null ? List.of() : changes);
    }

    public boolean hasChanges() {
        return !changes.isEmpty();
    }
}
