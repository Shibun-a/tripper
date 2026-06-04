package com.embabel.tripper.rag;

import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class TravelKnowledgeRepository {

    private final ConcurrentHashMap<String, TravelKnowledgeDocument> documents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<IndexedTravelKnowledgeChunk>> chunksByDocument = new ConcurrentHashMap<>();

    public TravelKnowledgeDocument save(
            TravelKnowledgeDocument document,
            List<IndexedTravelKnowledgeChunk> chunks
    ) {
        documents.put(document.getId(), document);
        chunksByDocument.put(document.getId(), List.copyOf(chunks));
        return document;
    }

    public List<TravelKnowledgeDocument> findAllDocuments() {
        return documents.values().stream()
                .sorted(Comparator.comparing(TravelKnowledgeDocument::getCreatedAt).reversed())
                .toList();
    }

    List<IndexedTravelKnowledgeChunk> findAllChunks() {
        List<IndexedTravelKnowledgeChunk> chunks = new ArrayList<>();
        chunksByDocument.values().forEach(chunks::addAll);
        return chunks;
    }

    public void clear() {
        documents.clear();
        chunksByDocument.clear();
    }
}
