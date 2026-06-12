package io.github.shibuna.tripsmith.agent

import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.api.common.create
import com.embabel.common.ai.model.LlmOptions
import io.github.shibuna.tripsmith.eval.JudgeScores

/**
 * Input to the LLM judge: the finished plan prose plus the eval case's (semi) ground-truth fields.
 */
data class PlanJudgeRequest(
    val planText: String,
    val interests: List<String>,
    val constraints: List<String>,
    val expectedThemes: List<String>,
    val expectedCountries: List<String>,
)

/**
 * Minimal single-action agent that scores a finished plan for the evaluation harness. It is a tiny
 * Embabel agent (rather than a raw LLM call) so it reuses the same proven structured-output path as
 * the planner (`context.ai().create<T>()`) and gets pricing/observability for free. It is only used
 * by the agent-backed evaluation tier; the normal travel-planning flow never reaches it.
 */
@Agent(description = "Score a finished travel plan for subjective quality (LLM-as-judge for evaluation)")
class PlanJudgeAgent(private val config: TripperConfig) {

    @AchievesGoal(description = "Produce judge scores for a proposed travel plan")
    @Action
    fun judgePlan(request: PlanJudgeRequest, context: OperationContext): JudgeScores {
        val prompt = """
            You are a strict, fair travel-plan evaluator. Score the plan below on five dimensions,
            each an integer from 1 (poor) to 5 (excellent). Be critical: reserve 5 for genuinely
            excellent work and do not inflate scores.

            Dimensions:
            - relevanceToInterests: does the plan serve the travellers' interests?
            - themeCoverage: how many of the expected themes does it actually cover?
            - routeSanity: is the day-by-day route geographically and time-wise plausible for the
              transport? Penalise back-tracking and impossible daily distances.
            - constraintAdherence: does it respect the constraints and the daily budget?
            - proseQuality: is the writing clear, engaging and useful?

            Travellers' interests: ${request.interests.joinToString(", ").ifBlank { "(none given)" }}
            Constraints: ${request.constraints.joinToString(", ").ifBlank { "(none given)" }}
            Expected themes: ${request.expectedThemes.joinToString(", ").ifBlank { "(none given)" }}
            Expected countries: ${request.expectedCountries.joinToString(", ").ifBlank { "(none given)" }}

            <plan>
            ${request.planText}
            </plan>

            Return the five integer scores and a one or two sentence rationale.
        """.trimIndent()

        return context.ai()
            .withLlm(LlmOptions.withModel(config.judgeModel).withTemperature(0.0))
            .create<JudgeScores>(prompt)
    }
}
