package io.github.shibuna.tripsmith.rag;

import java.util.List;

/**
 * Persistence port for travel-knowledge document metadata and the vector-store chunk ids that
 * belong to each document (so chunks can be removed when the knowledge base is cleared). The
 * chunk embeddings themselves live in the {@link org.springframework.ai.vectorstore.VectorStore}.
 */
public interface TravelKnowledgeDocumentStore {

    TravelKnowledgeDocument save(TravelKnowledgeDocument document, List<String> chunkIds);

    List<TravelKnowledgeDocument> findAllDocuments();

    List<String> allChunkIds();

    void clear();
}
