package com.embabel.tripper.rag;

import java.util.Map;

final class IndexedTravelKnowledgeChunk {

    private final String documentId;
    private final String documentTitle;
    private final TravelKnowledgeSourceType sourceType;
    private final String source;
    private final String chunkId;
    private final int chunkIndex;
    private final String text;
    private final Map<String, Integer> termVector;

    IndexedTravelKnowledgeChunk(
            String documentId,
            String documentTitle,
            TravelKnowledgeSourceType sourceType,
            String source,
            String chunkId,
            int chunkIndex,
            String text,
            Map<String, Integer> termVector
    ) {
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.sourceType = sourceType;
        this.source = source;
        this.chunkId = chunkId;
        this.chunkIndex = chunkIndex;
        this.text = text;
        this.termVector = Map.copyOf(termVector);
    }

    String getDocumentId() {
        return documentId;
    }

    String getDocumentTitle() {
        return documentTitle;
    }

    TravelKnowledgeSourceType getSourceType() {
        return sourceType;
    }

    String getSource() {
        return source;
    }

    String getChunkId() {
        return chunkId;
    }

    int getChunkIndex() {
        return chunkIndex;
    }

    String getText() {
        return text;
    }

    Map<String, Integer> getTermVector() {
        return termVector;
    }
}
