package com.embabel.tripper.rag;

import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores document metadata for the /knowledge listing and tracks which vector-store chunk ids
 * belong to each document (so they can be removed on clear). The chunk embeddings themselves
 * live in the {@link org.springframework.ai.vectorstore.VectorStore}.
 */
@Repository
public class TravelKnowledgeRepository {

    private final ConcurrentHashMap<String, TravelKnowledgeDocument> documents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<String>> chunkIdsByDocument = new ConcurrentHashMap<>();

    public TravelKnowledgeDocument save(
            TravelKnowledgeDocument document,
            List<String> chunkIds
    ) {
        documents.put(document.getId(), document);
        chunkIdsByDocument.put(document.getId(), List.copyOf(chunkIds));
        return document;
    }

    public List<TravelKnowledgeDocument> findAllDocuments() {
        return documents.values().stream()
                .sorted(Comparator.comparing(TravelKnowledgeDocument::getCreatedAt).reversed())
                .toList();
    }

    public List<String> allChunkIds() {
        List<String> ids = new ArrayList<>();
        chunkIdsByDocument.values().forEach(ids::addAll);
        return ids;
    }

    public void clear() {
        documents.clear();
        chunkIdsByDocument.clear();
    }
}
