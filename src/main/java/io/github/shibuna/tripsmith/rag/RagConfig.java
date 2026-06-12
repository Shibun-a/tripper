package io.github.shibuna.tripsmith.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Wires the local embedding model and the in-memory vector store used for travel-knowledge RAG.
 * Both go through Spring AI's {@link EmbeddingModel} / {@link VectorStore} interfaces, so the
 * implementation can later be swapped (e.g. a domestic embedding API or pgvector) without
 * touching the agent or the service.
 */
@Configuration
public class RagConfig {

    /**
     * Local ONNX embedding model (multilingual paraphrase-MiniLM by default, see
     * {@link RagProperties#getEmbeddingModelUri()}). Runs on CPU, offline after a one-time
     * model download — no cross-border API calls. {@code afterPropertiesSet()} loads the
     * model automatically when Spring creates the bean.
     */
    @Bean
    public TransformersEmbeddingModel travelKnowledgeEmbeddingModel(RagProperties properties) {
        TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel();
        embeddingModel.setModelResource(properties.getEmbeddingModelUri());
        embeddingModel.setTokenizerResource(properties.getEmbeddingTokenizerUri());
        return embeddingModel;
    }

    /**
     * In-memory vector store for knowledge chunks, explicitly bound to the local embedding model
     * so it does not pick up the OpenAI {@link EmbeddingModel} beans embabel also registers.
     */
    @Bean
    @Profile("!postgres")
    public VectorStore travelKnowledgeVectorStore(
            @Qualifier("travelKnowledgeEmbeddingModel") EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    /**
     * Durable vector store on the postgres profile. Built manually (not via the pgvector
     * starter autoconfiguration) so it stays bound to the local embedding model rather than
     * whichever {@link EmbeddingModel} autoconfiguration would pick.
     */
    @Bean
    @Profile("postgres")
    public VectorStore travelKnowledgePgVectorStore(
            @Qualifier("travelKnowledgeEmbeddingModel") EmbeddingModel embeddingModel,
            JdbcTemplate jdbcTemplate) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                // all-MiniLM-L6-v2 (and the planned multilingual default) embed at 384 dims.
                .dimensions(384)
                .initializeSchema(true)
                .build();
    }
}
