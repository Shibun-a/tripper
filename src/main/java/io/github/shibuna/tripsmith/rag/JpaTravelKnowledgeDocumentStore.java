package io.github.shibuna.tripsmith.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Database-backed knowledge-document store (postgres profile). */
@Repository
@Profile("postgres")
@Transactional
public class JpaTravelKnowledgeDocumentStore implements TravelKnowledgeDocumentStore {

    private final TravelKnowledgeDocumentJpaRepository repository;
    private final ObjectMapper objectMapper;

    public JpaTravelKnowledgeDocumentStore(
            TravelKnowledgeDocumentJpaRepository repository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public TravelKnowledgeDocument save(TravelKnowledgeDocument document, List<String> chunkIds) {
        repository.save(new TravelKnowledgeDocumentEntity(
                document.getId(),
                document.getCreatedAt(),
                writePayload(DocumentDoc.of(document, chunkIds))
        ));
        return document;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TravelKnowledgeDocument> findAllDocuments() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(entity -> toDoc(entity).toDocument())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> allChunkIds() {
        return repository.findAll().stream()
                .flatMap(entity -> toDoc(entity).chunkIds().stream())
                .toList();
    }

    @Override
    public void clear() {
        repository.deleteAll();
    }

    private DocumentDoc toDoc(TravelKnowledgeDocumentEntity entity) {
        try {
            return objectMapper.readValue(entity.getPayload(), DocumentDoc.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot read stored knowledge document " + entity.getId(), ex);
        }
    }

    private String writePayload(DocumentDoc doc) {
        try {
            return objectMapper.writeValueAsString(doc);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize knowledge document " + doc.id(), ex);
        }
    }

    record DocumentDoc(
            String id,
            String title,
            TravelKnowledgeSourceType sourceType,
            String source,
            String content,
            Instant createdAt,
            List<String> chunkIds
    ) {
        static DocumentDoc of(TravelKnowledgeDocument document, List<String> chunkIds) {
            return new DocumentDoc(
                    document.getId(),
                    document.getTitle(),
                    document.getSourceType(),
                    document.getSource(),
                    document.getContent(),
                    document.getCreatedAt(),
                    List.copyOf(chunkIds)
            );
        }

        TravelKnowledgeDocument toDocument() {
            return new TravelKnowledgeDocument(id, title, sourceType, source, content, createdAt);
        }
    }
}
