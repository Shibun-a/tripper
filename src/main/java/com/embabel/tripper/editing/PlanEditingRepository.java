package com.embabel.tripper.editing;

import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class PlanEditingRepository {

    private final Map<String, PlanEditSession> sessions = new LinkedHashMap<>();

    public synchronized PlanEditSession save(PlanEditSession session) {
        sessions.put(session.getRunId(), session);
        return session;
    }

    public synchronized Optional<PlanEditSession> findByRunId(String runId) {
        return Optional.ofNullable(sessions.get(runId));
    }

    public synchronized void clear() {
        sessions.clear();
    }
}
