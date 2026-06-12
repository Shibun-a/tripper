package com.embabel.tripper.rag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Row shape for stored knowledge-document metadata. The document plus its vector-store chunk
 * ids travel as one opaque JSON payload; the embeddings themselves live in pgvector.
 */
@Entity
@Table(name = "travel_knowledge_documents")
public class TravelKnowledgeDocumentEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 10_000_000)
    private String payload;

    protected TravelKnowledgeDocumentEntity() {
    }

    TravelKnowledgeDocumentEntity(String id, Instant createdAt, String payload) {
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
