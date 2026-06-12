package com.embabel.tripper.rag;

import com.embabel.tripper.safety.ContentSafetyService;
import com.embabel.tripper.safety.SafetyAssessment;
import com.embabel.tripper.safety.UrlImportGuard;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Imports travel knowledge, splits it into chunks, embeds each chunk with a local embedding model
 * and retrieves the most semantically relevant chunks for a query via a vector store. Replaces the
 * earlier keyword (term-frequency + cosine) retrieval; the public surface is unchanged so the agent
 * integration ({@code TripperAgent.retrieveTravelKnowledge}) is untouched.
 */
@Service
public class TravelKnowledgeService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]*");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "from", "that", "this", "into", "onto",
            "your", "you", "are", "was", "were", "will", "have", "has", "had",
            "trip", "travel", "route", "day", "days"
    );

    private static final String META_DOCUMENT_ID = "documentId";
    private static final String META_DOCUMENT_TITLE = "documentTitle";
    private static final String META_SOURCE_TYPE = "sourceType";
    private static final String META_SOURCE = "source";
    private static final String META_CHUNK_INDEX = "chunkIndex";

    private final TravelKnowledgeDocumentStore repository;
    private final RestClient restClient;
    private final ContentSafetyService contentSafetyService;
    private final UrlImportGuard urlImportGuard;
    private final VectorStore vectorStore;
    private final RagProperties properties;

    public TravelKnowledgeService(
            TravelKnowledgeDocumentStore repository,
            RestClient restClient,
            ContentSafetyService contentSafetyService,
            UrlImportGuard urlImportGuard,
            VectorStore vectorStore,
            RagProperties properties
    ) {
        this.repository = repository;
        this.restClient = restClient;
        this.contentSafetyService = contentSafetyService;
        this.urlImportGuard = urlImportGuard;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public TravelKnowledgeDocument addPastedText(
            String title,
            String content
    ) {
        return addDocument(
                title,
                TravelKnowledgeSourceType.PASTED_TEXT,
                "manual",
                content
        );
    }

    public TravelKnowledgeDocument addUploadedText(
            String title,
            String filename,
            String content
    ) {
        String displayTitle = isBlank(title) ? filename : title;
        return addDocument(
                displayTitle,
                TravelKnowledgeSourceType.UPLOADED_FILE,
                filename,
                content
        );
    }

    public TravelKnowledgeDocument addUrl(
            String url,
            String title
    ) {
        urlImportGuard.requireFetchable(url);
        byte[] rawBytes = restClient.get()
                .uri(url)
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        // Redirects land here too: the client never follows them, because a
                        // redirect target would bypass the SSRF host check above.
                        throw new IllegalStateException(
                                "URL returned status " + response.getStatusCode() + ": " + url);
                    }
                    return readBounded(response.getBody(), properties.getMaxUrlImportBytes());
                });
        if (rawBytes == null || rawBytes.length == 0) {
            throw new IllegalStateException("No response body from " + url);
        }
        String text = htmlToText(new String(rawBytes, StandardCharsets.UTF_8));
        return addDocument(
                isBlank(title) ? url : title,
                TravelKnowledgeSourceType.URL,
                url,
                text
        );
    }

    private byte[] readBounded(InputStream body, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = body.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            if (out.size() > maxBytes) {
                throw new IllegalArgumentException(
                        "URL content exceeds the import limit of " + maxBytes + " bytes");
            }
        }
        return out.toByteArray();
    }

    public List<TravelKnowledgeDocument> documents() {
        return repository.findAllDocuments();
    }

    public void clear() {
        List<String> chunkIds = repository.allChunkIds();
        if (!chunkIds.isEmpty()) {
            vectorStore.delete(chunkIds);
        }
        repository.clear();
    }

    public TravelKnowledgeContext retrieveForQuery(
            String query,
            int limit
    ) {
        return new TravelKnowledgeContext(query, search(query, limit));
    }

    public List<TravelKnowledgeHit> search(
            String query
    ) {
        return search(query, properties.getTopK());
    }

    public List<TravelKnowledgeHit> search(
            String query,
            int limit
    ) {
        if (isBlank(query)) {
            return List.of();
        }

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(Math.max(1, limit))
                .similarityThreshold(properties.getSimilarityThreshold())
                .build();

        List<Document> results = vectorStore.similaritySearch(request);
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        Set<String> queryTokens = new HashSet<>(tokenize(query));
        List<TravelKnowledgeHit> hits = new ArrayList<>();
        for (Document result : results) {
            hits.add(toHit(result, queryTokens));
        }
        return hits;
    }

    private TravelKnowledgeHit toHit(
            Document result,
            Set<String> queryTokens
    ) {
        Map<String, Object> metadata = result.getMetadata();
        String documentId = asString(metadata.get(META_DOCUMENT_ID));
        String documentTitle = asString(metadata.get(META_DOCUMENT_TITLE));
        TravelKnowledgeSourceType sourceType = TravelKnowledgeSourceType.valueOf(
                asString(metadata.get(META_SOURCE_TYPE)));
        String source = asString(metadata.get(META_SOURCE));
        int chunkIndex = metadata.get(META_CHUNK_INDEX) instanceof Number n ? n.intValue() : 0;
        String text = result.getText() == null ? "" : result.getText();
        double score = result.getScore() == null ? 0.0 : result.getScore();

        // Keyword overlap is no longer used for ranking; we keep it only as a display annotation
        // in the retrieval-debug view.
        Set<String> matched = new HashSet<>(queryTokens);
        matched.retainAll(new HashSet<>(tokenize(text)));
        List<String> matchedTerms = matched.stream().sorted().toList();

        SafetyAssessment safetyAssessment = contentSafetyService.assessUntrustedContent(source, text);

        return new TravelKnowledgeHit(
                documentId,
                documentTitle,
                sourceType,
                source,
                result.getId(),
                chunkIndex,
                text,
                contentSafetyService.sanitizeUntrustedTextForPrompt(text, safetyAssessment),
                score,
                matchedTerms,
                safetyAssessment
        );
    }

    private TravelKnowledgeDocument addDocument(
            String title,
            TravelKnowledgeSourceType sourceType,
            String source,
            String content
    ) {
        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.isBlank()) {
            throw new IllegalArgumentException("Knowledge content must not be blank");
        }

        TravelKnowledgeDocument document = new TravelKnowledgeDocument(
                UUID.randomUUID().toString(),
                isBlank(title) ? "Untitled travel knowledge" : title,
                sourceType,
                source,
                normalizedContent
        );

        List<String> textChunks = chunkText(
                normalizedContent,
                properties.getChunkSizeWords(),
                properties.getChunkOverlapWords());

        List<Document> vectorDocuments = new ArrayList<>();
        List<String> chunkIds = new ArrayList<>();
        int chunkIndex = 0;
        for (String textChunk : textChunks) {
            if (textChunk.isBlank()) {
                continue;
            }
            Map<String, Object> metadata = new HashMap<>();
            metadata.put(META_DOCUMENT_ID, document.getId());
            metadata.put(META_DOCUMENT_TITLE, document.getTitle());
            metadata.put(META_SOURCE_TYPE, document.getSourceType().name());
            metadata.put(META_SOURCE, document.getSource());
            metadata.put(META_CHUNK_INDEX, chunkIndex++);

            Document vectorDocument = new Document(textChunk, metadata);
            vectorDocuments.add(vectorDocument);
            chunkIds.add(vectorDocument.getId());
        }

        if (vectorDocuments.isEmpty()) {
            throw new IllegalArgumentException("Knowledge content did not contain searchable text");
        }

        vectorStore.add(vectorDocuments);
        return repository.save(document, chunkIds);
    }

    private List<String> chunkText(
            String text,
            int maxWords,
            int overlapWords
    ) {
        List<String> words = List.of(text.replaceAll("\\s+", " ").trim().split(" ")).stream()
                .filter(word -> !word.isBlank())
                .toList();

        if (words.size() <= maxWords) {
            return List.of(String.join(" ", words));
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        int step = Math.max(1, maxWords - overlapWords);
        while (start < words.size()) {
            int end = Math.min(start + maxWords, words.size());
            chunks.add(String.join(" ", words.subList(start, end)));
            if (end == words.size()) {
                break;
            }
            start += step;
        }
        return chunks;
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        var matcher = TOKEN_PATTERN.matcher(text.toLowerCase());
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 3 && !STOP_WORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String htmlToText(String html) {
        return html
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
