package io.github.shibuna.tripsmith.verification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Database-backed verifier-result store (postgres profile), keeping the same bounded recent
 * window as the in-memory tier. PlanVerificationResult and its nested types serialize directly:
 * each has a single public constructor whose parameter names match its getters.
 */
@Repository
@Profile("postgres")
@Transactional
public class JpaPlanVerificationResultStore implements PlanVerificationResultStore {

    private static final int MAX_RESULTS = 100;

    private final PlanVerificationResultJpaRepository repository;
    private final ObjectMapper objectMapper;

    public JpaPlanVerificationResultStore(
            PlanVerificationResultJpaRepository repository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public PlanVerificationResult save(PlanVerificationResult result) {
        repository.save(new PlanVerificationResultEntity(
                result.getId(),
                result.getCreatedAt(),
                writePayload(result)
        ));
        List<PlanVerificationResultEntity> oldestFirst = repository.findAllByOrderByCreatedAtAsc();
        int excess = oldestFirst.size() - MAX_RESULTS;
        if (excess > 0) {
            repository.deleteAll(oldestFirst.subList(0, excess));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PlanVerificationResult> findById(String id) {
        return repository.findById(id).map(this::toResult);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanVerificationResult> findRecent() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toResult).toList();
    }

    private PlanVerificationResult toResult(PlanVerificationResultEntity entity) {
        try {
            return objectMapper.readValue(entity.getPayload(), PlanVerificationResult.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot read stored verification result " + entity.getId(), ex);
        }
    }

    private String writePayload(PlanVerificationResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize verification result " + result.getId(), ex);
        }
    }
}
