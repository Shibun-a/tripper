package com.embabel.tripper.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline, deterministic embedding for fast plumbing/safety tests: a normalized hashed
 * bag-of-words vector. Similarity reflects word overlap (enough to exercise the vector-store
 * wiring), without downloading the real model. Semantic-quality assertions use the real
 * local model instead.
 */
final class DeterministicEmbeddingModel extends AbstractEmbeddingModel {

    private static final int DIM = 64;

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIM];
        if (text != null) {
            for (String token : text.toLowerCase().split("[^a-z0-9]+")) {
                if (!token.isBlank()) {
                    vector[Math.abs(token.hashCode()) % DIM] += 1f;
                }
            }
        }
        double norm = 0.0;
        for (float value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < DIM; i++) {
                vector[i] /= (float) norm;
            }
        }
        return vector;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> instructions = request.getInstructions();
        for (int i = 0; i < instructions.size(); i++) {
            embeddings.add(new Embedding(embed(instructions.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public int dimensions() {
        return DIM;
    }
}
