package com.embabel.tripper.editing;

import java.util.Optional;

/** Persistence port for plan edit sessions, keyed by the Embabel process id. */
public interface PlanEditSessionStore {

    PlanEditSession save(PlanEditSession session);

    Optional<PlanEditSession> findByRunId(String runId);

    void clear();
}
