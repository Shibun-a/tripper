package io.github.shibuna.tripsmith.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunable knobs for the RAG retrieval pipeline. Exposed so retrieval quality can be optimized
 * (and the optimization measured) without code changes.
 */
@ConfigurationProperties("tripsmith.rag")
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

    /**
     * Upper bound in bytes for a URL import response body. The body is read incrementally and
     * the import aborts past this limit, so a huge page cannot exhaust memory.
     */
    private int maxUrlImportBytes = 2_000_000;

    /**
     * ONNX embedding model for chunk and query embeddings. The default is multilingual (50+
     * languages including Chinese) because the app explicitly supports Chinese briefs and
     * knowledge documents; the earlier all-MiniLM-L6-v2 default embedded English only. Both
     * models are 384-dimensional, matching the pgvector schema. ~470MB one-time download,
     * cached locally afterwards.
     */
    private String embeddingModelUri =
            "https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/onnx/model.onnx";

    /** Fast-tokenizer definition matching {@link #embeddingModelUri}. */
    private String embeddingTokenizerUri =
            "https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/tokenizer.json";

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

    public int getMaxUrlImportBytes() {
        return maxUrlImportBytes;
    }

    public void setMaxUrlImportBytes(int maxUrlImportBytes) {
        this.maxUrlImportBytes = maxUrlImportBytes;
    }

    public String getEmbeddingModelUri() {
        return embeddingModelUri;
    }

    public void setEmbeddingModelUri(String embeddingModelUri) {
        this.embeddingModelUri = embeddingModelUri;
    }

    public String getEmbeddingTokenizerUri() {
        return embeddingTokenizerUri;
    }

    public void setEmbeddingTokenizerUri(String embeddingTokenizerUri) {
        this.embeddingTokenizerUri = embeddingTokenizerUri;
    }
}
