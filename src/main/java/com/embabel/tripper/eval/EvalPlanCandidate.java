package com.embabel.tripper.eval;

import com.embabel.tripper.verification.ItineraryVerificationRequest;

public record EvalPlanCandidate(
        ItineraryVerificationRequest verificationRequest,
        long latencyMs,
        double estimatedTokenCostUsd,
        int toolCallAttempts,
        int successfulToolCalls
) {
}
