package io.github.shibuna.tripsmith.rag;

import java.time.Instant;

public final class TravelKnowledgeDocument {

    private final String id;
    private final String title;
    private final TravelKnowledgeSourceType sourceType;
    private final String source;
    private final String content;
    private final Instant createdAt;

    public TravelKnowledgeDocument(
            String id,
            String title,
            TravelKnowledgeSourceType sourceType,
            String source,
            String content
    ) {
        this(id, title, sourceType, source, content, Instant.now());
    }

    public TravelKnowledgeDocument(
            String id,
            String title,
            TravelKnowledgeSourceType sourceType,
            String source,
            String content,
            Instant createdAt
    ) {
        this.id = id;
        this.title = title;
        this.sourceType = sourceType;
        this.source = source;
        this.content = content;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public TravelKnowledgeSourceType getSourceType() {
        return sourceType;
    }

    public String getSource() {
        return source;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getSummary() {
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 220 ? normalized : normalized.substring(0, 220);
    }
}
