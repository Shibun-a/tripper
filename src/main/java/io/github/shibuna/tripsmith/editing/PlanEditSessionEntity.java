package io.github.shibuna.tripsmith.editing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Row shape for a stored edit session: the session (constraints, versions, diffs, verifier
 * results) travels as one opaque JSON payload; createdAt exists as a column only to order
 * eviction of the oldest sessions.
 */
@Entity
@Table(name = "plan_edit_sessions")
public class PlanEditSessionEntity {

    @Id
    private String runId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 10_000_000)
    private String payload;

    protected PlanEditSessionEntity() {
    }

    PlanEditSessionEntity(String runId, Instant createdAt, String payload) {
        this.runId = runId;
        this.createdAt = createdAt;
        this.payload = payload;
    }

    String getRunId() {
        return runId;
    }

    String getPayload() {
        return payload;
    }

    void setPayload(String payload) {
        this.payload = payload;
    }
}
