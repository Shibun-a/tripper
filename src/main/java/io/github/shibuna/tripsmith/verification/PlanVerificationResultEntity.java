package io.github.shibuna.tripsmith.verification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Row shape for stored verifier results; the result is an opaque JSON payload. */
@Entity
@Table(name = "plan_verification_results")
public class PlanVerificationResultEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 10_000_000)
    private String payload;

    protected PlanVerificationResultEntity() {
    }

    PlanVerificationResultEntity(String id, Instant createdAt, String payload) {
        this.id = id;
        this.createdAt = createdAt;
        this.payload = payload;
    }

    String getId() {
        return id;
    }

    String getPayload() {
        return payload;
    }
}
