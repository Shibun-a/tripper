package com.embabel.tripper.editing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Database-backed edit-session store (postgres profile), capped like the in-memory tier. */
@Repository
@Profile("postgres")
@Transactional
public class JpaPlanEditSessionStore implements PlanEditSessionStore {

    private static final int MAX_SESSIONS = 100;

    private final PlanEditSessionJpaRepository repository;
    private final ObjectMapper objectMapper;

    public JpaPlanEditSessionStore(PlanEditSessionJpaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public PlanEditSession save(PlanEditSession session) {
        String payload = writePayload(SessionDoc.of(session));
        repository.findById(session.getRunId()).ifPresentOrElse(
                entity -> entity.setPayload(payload),
                () -> repository.save(new PlanEditSessionEntity(session.getRunId(), session.getCreatedAt(), payload))
        );
        List<PlanEditSessionEntity> oldestFirst = repository.findAllByOrderByCreatedAtAsc();
        int excess = oldestFirst.size() - MAX_SESSIONS;
        if (excess > 0) {
            repository.deleteAll(oldestFirst.subList(0, excess));
        }
        return session;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PlanEditSession> findByRunId(String runId) {
        return repository.findById(runId).map(this::toSession);
    }

    @Override
    public void clear() {
        repository.deleteAll();
    }

    private PlanEditSession toSession(PlanEditSessionEntity entity) {
        try {
            return objectMapper.readValue(entity.getPayload(), SessionDoc.class).toSession();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot read stored edit session " + entity.getRunId(), ex);
        }
    }

    private String writePayload(SessionDoc doc) {
        try {
            return objectMapper.writeValueAsString(doc);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize edit session " + doc.runId(), ex);
        }
    }

    record SessionDoc(
            String runId,
            String title,
            String fromLocation,
            String toLocation,
            String transportPreference,
            LocalDate departureDate,
            LocalDate returnDate,
            double dailyBudget,
            String constraintsSummary,
            Instant createdAt,
            List<PlanEditVersion> versions
    ) {
        static SessionDoc of(PlanEditSession session) {
            return new SessionDoc(
                    session.getRunId(),
                    session.getTitle(),
                    session.getFromLocation(),
                    session.getToLocation(),
                    session.getTransportPreference(),
                    session.getDepartureDate(),
                    session.getReturnDate(),
                    session.getDailyBudget(),
                    session.getConstraintsSummary(),
                    session.getCreatedAt(),
                    session.getVersions()
            );
        }

        PlanEditSession toSession() {
            PlanEditSession session = new PlanEditSession(
                    runId, title, fromLocation, toLocation, transportPreference,
                    departureDate, returnDate, dailyBudget, constraintsSummary, createdAt
            );
            if (versions != null) {
                versions.forEach(session::addVersion);
            }
            return session;
        }
    }
}
