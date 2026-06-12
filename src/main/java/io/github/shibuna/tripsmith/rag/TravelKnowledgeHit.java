package io.github.shibuna.tripsmith.rag;

import io.github.shibuna.tripsmith.safety.SafetyAssessment;

import java.util.List;

public final class TravelKnowledgeHit {

    private final String documentId;
    private final String documentTitle;
    private final TravelKnowledgeSourceType sourceType;
    private final String source;
    private final String chunkId;
    private final int chunkIndex;
    private final String text;
    private final String promptText;
    private final double score;
    private final List<String> matchedTerms;
    private final SafetyAssessment safetyAssessment;

    public TravelKnowledgeHit(
            String documentId,
            String documentTitle,
            TravelKnowledgeSourceType sourceType,
            String source,
            String chunkId,
            int chunkIndex,
            String text,
            String promptText,
            double score,
            List<String> matchedTerms,
            SafetyAssessment safetyAssessment
    ) {
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.sourceType = sourceType;
        this.source = source;
        this.chunkId = chunkId;
        this.chunkIndex = chunkIndex;
        this.text = text;
        this.promptText = promptText;
        this.score = score;
        this.matchedTerms = List.copyOf(matchedTerms);
        this.safetyAssessment = safetyAssessment == null ? SafetyAssessment.safe(source) : safetyAssessment;
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

    public String getPromptText() {
        return promptText;
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

    public SafetyAssessment getSafetyAssessment() {
        return safetyAssessment;
    }

    public boolean isHasSafetyFindings() {
        return safetyAssessment.isHasFindings();
    }
}
