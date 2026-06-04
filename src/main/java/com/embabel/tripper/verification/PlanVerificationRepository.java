package com.embabel.tripper.verification;

import org.springframework.stereotype.Repository;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class PlanVerificationRepository {

    private static final int MAX_RESULTS = 100;

    private final Map<String, PlanVerificationResult> results = new ConcurrentHashMap<>();
    private final Deque<String> resultOrder = new ArrayDeque<>();

    public synchronized PlanVerificationResult save(PlanVerificationResult result) {
        results.put(result.getId(), result);
        resultOrder.remove(result.getId());
        resultOrder.addFirst(result.getId());
        while (resultOrder.size() > MAX_RESULTS) {
            String removed = resultOrder.removeLast();
            results.remove(removed);
        }
        return result;
    }

    public synchronized Optional<PlanVerificationResult> findById(String id) {
        return Optional.ofNullable(results.get(id));
    }

    public synchronized List<PlanVerificationResult> findRecent() {
        List<PlanVerificationResult> recent = new ArrayList<>();
        for (String id : resultOrder) {
            PlanVerificationResult result = results.get(id);
            if (result != null) {
                recent.add(result);
            }
        }
        return recent;
    }
}
