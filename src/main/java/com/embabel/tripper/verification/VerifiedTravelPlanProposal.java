package com.embabel.tripper.verification;

import com.embabel.tripper.agent.ProposedTravelPlan;

public final class VerifiedTravelPlanProposal {

    private final ProposedTravelPlan proposal;
    private final PlanVerificationResult verificationResult;

    public VerifiedTravelPlanProposal(
            ProposedTravelPlan proposal,
            PlanVerificationResult verificationResult
    ) {
        this.proposal = proposal;
        this.verificationResult = verificationResult;
    }

    public ProposedTravelPlan getProposal() {
        return proposal;
    }

    public PlanVerificationResult getVerificationResult() {
        return verificationResult;
    }

    public boolean isRepaired() {
        return verificationResult.isRepaired();
    }

    public int getRepairAttempts() {
        return verificationResult.getRepairAttempts();
    }
}
