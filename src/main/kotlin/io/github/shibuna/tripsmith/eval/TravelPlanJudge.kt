package io.github.shibuna.tripsmith.eval

import com.embabel.agent.core.AgentPlatform
import com.embabel.agent.core.ProcessOptions
import io.github.shibuna.tripsmith.agent.PlanJudgeRequest
import org.springframework.stereotype.Component

/**
 * Runs [io.github.shibuna.tripsmith.agent.PlanJudgeAgent] for a finished plan and returns its [JudgeScores].
 * Wrapping the judge agent in a plain Spring bean lets the (Java) evaluation harness depend on a
 * simple `judge(request)` call without touching the agent platform directly. Only the agent-backed
 * evaluation tier wires this in; the deterministic CI tier leaves the harness's judge null.
 */
@Component
class TravelPlanJudge(private val agentPlatform: AgentPlatform) : PlanJudge {

    override fun judge(
        planText: String,
        interests: List<String>,
        constraints: List<String>,
        expectedThemes: List<String>,
        expectedCountries: List<String>,
    ): JudgeScores {
        val agent = agentPlatform.agents().firstOrNull {
            it.name.contains("PlanJudge", ignoreCase = true) ||
                it.description.contains("LLM-as-judge", ignoreCase = true)
        } ?: error("PlanJudgeAgent is not deployed on the agent platform")

        val request = PlanJudgeRequest(planText, interests, constraints, expectedThemes, expectedCountries)
        val process = agentPlatform.createAgentProcessFrom(agent, ProcessOptions.DEFAULT, request)
        val finished = agentPlatform.start(process).get()
        return finished.resultOfType(JudgeScores::class.java)
    }
}
