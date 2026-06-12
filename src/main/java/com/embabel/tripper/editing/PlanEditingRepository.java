package com.embabel.tripper.editing;

import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class PlanEditingRepository {

    // Same bound pattern as PlanVerificationRepository: a session holds full plan HTML per
    // version, so an unbounded map is a slow leak on a long-running instance.
    private static final int MAX_SESSIONS = 100;

    private final Map<String, PlanEditSession> sessions = new LinkedHashMap<>();

    public synchronized PlanEditSession save(PlanEditSession session) {
        // Remove first so a re-saved session moves to the back of the eviction order.
        sessions.remove(session.getRunId());
        sessions.put(session.getRunId(), session);
        while (sessions.size() > MAX_SESSIONS) {
            String oldest = sessions.keySet().iterator().next();
            sessions.remove(oldest);
        }
        return session;
    }

    public synchronized Optional<PlanEditSession> findByRunId(String runId) {
        return Optional.ofNullable(sessions.get(runId));
    }

    public synchronized void clear() {
        sessions.clear();
    }
}
