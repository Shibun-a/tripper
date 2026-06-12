package com.embabel.tripper.observability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Row shape for a stored run trace. Queryable fields are real columns; the full trace
 * (including its event timeline) is an opaque JSON payload — SQL never needs to look inside
 * it, and a plain varchar stays portable across Postgres and the H2 used in adapter tests.
 */
@Entity
@Table(name = "agent_run_traces")
public class AgentRunTraceEntity {

    @Id
    private String runId;

    @Column(nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    private AgentRunStatus status;

    private Double costUsd;

    @Column(nullable = false, length = 10_000_000)
    private String payload;

    protected AgentRunTraceEntity() {
    }

    AgentRunTraceEntity(String runId, Instant createdAt, AgentRunStatus status, Double costUsd, String payload) {
        this.runId = runId;
        this.createdAt = createdAt;
        this.status = status;
        this.costUsd = costUsd;
        this.payload = payload;
    }

    String getRunId() {
        return runId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    String getPayload() {
        return payload;
    }

    void update(AgentRunStatus status, Double costUsd, String payload) {
        this.status = status;
        this.costUsd = costUsd;
        this.payload = payload;
    }
}
