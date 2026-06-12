package io.github.shibuna.tripsmith.rag;

import org.springframework.context.annotation.Profile;
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
@Profile("!postgres")
public class TravelKnowledgeRepository implements TravelKnowledgeDocumentStore {

    private final ConcurrentHashMap<String, TravelKnowledgeDocument> documents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<String>> chunkIdsByDocument = new ConcurrentHashMap<>();

    @Override
    public TravelKnowledgeDocument save(
            TravelKnowledgeDocument document,
            List<String> chunkIds
    ) {
        documents.put(document.getId(), document);
        chunkIdsByDocument.put(document.getId(), List.copyOf(chunkIds));
        return document;
    }

    @Override
    public List<TravelKnowledgeDocument> findAllDocuments() {
        return documents.values().stream()
                .sorted(Comparator.comparing(TravelKnowledgeDocument::getCreatedAt).reversed())
                .toList();
    }

    @Override
    public List<String> allChunkIds() {
        List<String> ids = new ArrayList<>();
        chunkIdsByDocument.values().forEach(ids::addAll);
        return ids;
    }

    @Override
    public void clear() {
        documents.clear();
        chunkIdsByDocument.clear();
    }
}
