package com.embabel.tripper.eval;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * LLM-as-judge scores for a finished travel plan, covering the subjective qualities a deterministic
 * verifier cannot measure (relevance, coherence, prose). Each dimension is 1 (poor) to 5 (excellent).
 *
 * <p>{@code routeSanity} deliberately overlaps with the verifier's route check to compensate for its
 * blind spot: the verifier only knows ~30 hard-coded cities, so most real routes degrade to INFO and
 * geographic zig-zags slip through (see ItineraryVerificationService). The judge reads the prose and
 * can flag an implausible route the rules missed.
 */
public record JudgeScores(
        @JsonPropertyDescription("1-5: how well the plan matches the travellers' stated interests")
        int relevanceToInterests,
        @JsonPropertyDescription("1-5: how many of the expected themes the plan actually covers")
        int themeCoverage,
        @JsonPropertyDescription("1-5: geographic and time plausibility of the day-by-day route for the chosen transport")
        int routeSanity,
        @JsonPropertyDescription("1-5: how well the plan respects the stated constraints and daily budget")
        int constraintAdherence,
        @JsonPropertyDescription("1-5: clarity, engagement and usefulness of the written itinerary")
        int proseQuality,
        @JsonPropertyDescription("One or two sentences justifying the scores")
        String rationale
) {

    /** Mean of the five 1-5 dimensions; convenient single number for reports. */
    public double averageScore() {
        return (relevanceToInterests + themeCoverage + routeSanity + constraintAdherence + proseQuality) / 5.0;
    }
}
