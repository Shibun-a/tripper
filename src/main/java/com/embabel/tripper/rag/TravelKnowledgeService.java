package com.embabel.tripper.rag;

import com.embabel.tripper.safety.ContentSafetyService;
import com.embabel.tripper.safety.SafetyAssessment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class TravelKnowledgeService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]*");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "from", "that", "this", "into", "onto",
            "your", "you", "are", "was", "were", "will", "have", "has", "had",
            "trip", "travel", "route", "day", "days"
    );

    private final TravelKnowledgeRepository repository;
    private final RestClient restClient;
    private final ContentSafetyService contentSafetyService;

    public TravelKnowledgeService(
            TravelKnowledgeRepository repository,
            RestClient restClient,
            ContentSafetyService contentSafetyService
    ) {
        this.repository = repository;
        this.restClient = restClient;
        this.contentSafetyService = contentSafetyService;
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
        String raw = restClient.get()
                .uri(url)
                .retrieve()
                .body(String.class);
        if (raw == null) {
            throw new IllegalStateException("No response body from " + url);
        }
        String text = htmlToText(raw);
        return addDocument(
                isBlank(title) ? url : title,
                TravelKnowledgeSourceType.URL,
                url,
                text
        );
    }

    public List<TravelKnowledgeDocument> documents() {
        return repository.findAllDocuments();
    }

    public void clear() {
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
        return search(query, 8);
    }

    public List<TravelKnowledgeHit> search(
            String query,
            int limit
    ) {
        Map<String, Integer> queryVector = termVector(query);
        if (queryVector.isEmpty()) {
            return List.of();
        }

        return repository.findAllChunks().stream()
                .map(chunk -> toHit(queryVector, chunk))
                .filter(hit -> hit.getScore() > 0.0)
                .sorted(Comparator.comparingDouble(TravelKnowledgeHit::getScore).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    private TravelKnowledgeHit toHit(
            Map<String, Integer> queryVector,
            IndexedTravelKnowledgeChunk chunk
    ) {
        double score = cosineSimilarity(queryVector, chunk.getTermVector());
        Set<String> matched = new HashSet<>(queryVector.keySet());
        matched.retainAll(chunk.getTermVector().keySet());
        List<String> matchedTerms = matched.stream().sorted().toList();
        SafetyAssessment safetyAssessment = contentSafetyService.assessUntrustedContent(
                chunk.getSource(),
                chunk.getText()
        );

        return new TravelKnowledgeHit(
                chunk.getDocumentId(),
                chunk.getDocumentTitle(),
                chunk.getSourceType(),
                chunk.getSource(),
                chunk.getChunkId(),
                chunk.getChunkIndex(),
                chunk.getText(),
                contentSafetyService.sanitizeUntrustedTextForPrompt(chunk.getText(), safetyAssessment),
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

        List<IndexedTravelKnowledgeChunk> chunks = new ArrayList<>();
        List<String> textChunks = chunkText(normalizedContent, 140, 25);
        for (int i = 0; i < textChunks.size(); i++) {
            String textChunk = textChunks.get(i);
            Map<String, Integer> termVector = termVector(textChunk);
            if (!termVector.isEmpty()) {
                chunks.add(new IndexedTravelKnowledgeChunk(
                        document.getId(),
                        document.getTitle(),
                        document.getSourceType(),
                        document.getSource(),
                        document.getId() + "-" + i,
                        i,
                        textChunk,
                        termVector
                ));
            }
        }

        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Knowledge content did not contain searchable text");
        }
        return repository.save(document, chunks);
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

    private Map<String, Integer> termVector(String text) {
        Map<String, Integer> vector = new HashMap<>();
        for (String token : tokenize(text)) {
            vector.merge(token, 1, Integer::sum);
        }
        return vector;
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

    private double cosineSimilarity(
            Map<String, Integer> left,
            Map<String, Integer> right
    ) {
        Set<String> common = new HashSet<>(left.keySet());
        common.retainAll(right.keySet());
        if (common.isEmpty()) {
            return 0.0;
        }

        double dot = 0.0;
        for (String term : common) {
            dot += left.getOrDefault(term, 0) * right.getOrDefault(term, 0);
        }

        double leftMagnitude = magnitude(left);
        double rightMagnitude = magnitude(right);
        if (leftMagnitude == 0.0 || rightMagnitude == 0.0) {
            return 0.0;
        }
        return dot / (leftMagnitude * rightMagnitude);
    }

    private double magnitude(Map<String, Integer> vector) {
        int sum = 0;
        for (int value : vector.values()) {
            sum += value * value;
        }
        return Math.sqrt(sum);
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
