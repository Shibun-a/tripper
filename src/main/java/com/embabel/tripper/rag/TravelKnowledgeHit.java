package com.embabel.tripper.rag;

import java.util.List;

public final class TravelKnowledgeHit {

    private final String documentId;
    private final String documentTitle;
    private final TravelKnowledgeSourceType sourceType;
    private final String source;
    private final String chunkId;
    private final int chunkIndex;
    private final String text;
    private final double score;
    private final List<String> matchedTerms;

    public TravelKnowledgeHit(
            String documentId,
            String documentTitle,
            TravelKnowledgeSourceType sourceType,
            String source,
            String chunkId,
            int chunkIndex,
            String text,
            double score,
            List<String> matchedTerms
    ) {
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.sourceType = sourceType;
        this.source = source;
        this.chunkId = chunkId;
        this.chunkIndex = chunkIndex;
        this.text = text;
        this.score = score;
        this.matchedTerms = List.copyOf(matchedTerms);
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public TravelKnowledgeSourceType getSourceType() {
        return sourceType;
    }

    public String getSource() {
        return source;
    }

    public String getChunkId() {
        return chunkId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getText() {
        return text;
    }

    public double getScore() {
        return score;
    }

    public List<String> getMatchedTerms() {
        return matchedTerms;
    }

    public String getCitationId() {
        return documentTitle + "#" + (chunkIndex + 1);
    }

    public String getMatchedTermsText() {
        return String.join(", ", matchedTerms);
    }
}
