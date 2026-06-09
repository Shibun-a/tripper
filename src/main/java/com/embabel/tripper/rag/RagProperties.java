package com.embabel.tripper.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunable knobs for the RAG retrieval pipeline. Exposed so retrieval quality can be optimized
 * (and the optimization measured) without code changes.
 */
@ConfigurationProperties("embabel.tripper.rag")
public class RagProperties {

    /** Words per chunk when splitting a document. */
    private int chunkSizeWords = 140;

    /** Overlap in words between consecutive chunks, to avoid losing context at boundaries. */
    private int chunkOverlapWords = 25;

    /** Default number of chunks to retrieve for a query. */
    private int topK = 6;

    /**
     * Minimum cosine similarity for a chunk to be returned. 0.0 keeps the top-k unfiltered;
     * raise it to drop weakly-related chunks (less prompt noise, fewer tokens).
     */
    private double similarityThreshold = 0.0;

    public int getChunkSizeWords() {
        return chunkSizeWords;
    }

    public void setChunkSizeWords(int chunkSizeWords) {
        this.chunkSizeWords = chunkSizeWords;
    }

    public int getChunkOverlapWords() {
        return chunkOverlapWords;
    }

    public void setChunkOverlapWords(int chunkOverlapWords) {
        this.chunkOverlapWords = chunkOverlapWords;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }
}
