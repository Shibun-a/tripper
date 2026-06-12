package io.github.shibuna.tripsmith.eval;

import io.github.shibuna.tripsmith.verification.ItineraryVerificationRequest;

public record EvalPlanCandidate(
        ItineraryVerificationRequest verificationRequest,
        long latencyMs,
        double estimatedTokenCostUsd,
        int toolCallAttempts,
        int successfulToolCalls
) {
}
