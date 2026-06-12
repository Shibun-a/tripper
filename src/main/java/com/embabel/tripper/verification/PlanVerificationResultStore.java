package com.embabel.tripper.verification;

import java.util.List;
import java.util.Optional;

/** Persistence port for verifier results; implementations keep only a bounded recent window. */
public interface PlanVerificationResultStore {

    PlanVerificationResult save(PlanVerificationResult result);

    Optional<PlanVerificationResult> findById(String id);

    List<PlanVerificationResult> findRecent();
}
